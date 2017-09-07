package jUnit;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.sloeber.core.api.BoardsManager;
import io.sloeber.core.api.CodeDescriptor;
import io.sloeber.core.api.CompileOptions;
import io.sloeber.core.api.ConfigurationDescriptor;
import io.sloeber.core.api.Sketch;
import io.sloeber.core.common.ConfigurationPreferences;
import jUnit.boards.Due;
import jUnit.boards.IBoard;
import jUnit.boards.NodeMCUBoard;
import jUnit.boards.Teensy3_1Board;
import jUnit.boards.UnoBoard;
import jUnit.boards.YunBoard;
import jUnit.boards.Zero;
import jUnit.boards.arduino101Board;
import jUnit.boards.friedBoard;
import jUnit.boards.leonardoBoard;
import jUnit.boards.megaBoard;
import jUnit.boards.wemosD1;

@SuppressWarnings("nls")
@RunWith(Parameterized.class)
public class CompileAndUpload {
	private static final boolean reinstall_boards_and_libraries = false;
	private static int mCounter = 0;
	private IBoard myBoard;
	private String myName;
	private static String interval = "1500";// change between 1500 and 100

	public CompileAndUpload(String name, IBoard board) {
		this.myBoard = board;
		this.myName = name;

	}

	@SuppressWarnings("rawtypes")
	@Parameters(name = "{index}: {0}")
	public static Collection examples() {
		WaitForInstallerToFinish();

		try {
			File file = ConfigurationPreferences.getInstallationPath().append("test.properties").toFile();
			if (!file.exists()) {
				file.createNewFile();
			}
			try(FileInputStream fileInput = new FileInputStream(file)){
			Properties properties = new Properties();
			properties.load(fileInput);
			fileInput.close();


			String key = "Last Used Blink Interval";
			interval = properties.getProperty(key);

			if ("100".equals(interval)) {
				interval = "1500";
			} else {
				interval = "100";
			}
			properties.put(key, interval);
			try(FileOutputStream fileOutput = new FileOutputStream(file)){
			properties.store(fileOutput, "This is a file with values for unit testing");
			fileOutput.close();
			}
			}

		} catch (IOException e1) {
			e1.printStackTrace();
		}



		IBoard[] boards = { new NodeMCUBoard(), new Teensy3_1Board(), new wemosD1(), new friedBoard(), new YunBoard(),
				new UnoBoard(),
				new megaBoard(),
				new Zero(), new Due(),
				new leonardoBoard(),
				new arduino101Board() };
		// , new NodeMCUBoard()
		LinkedList<Object[]> examples = new LinkedList<>();

		for (IBoard curBoard : boards) {
			examples.add(new Object[] { curBoard.getName(), curBoard });
		}

		return examples;
	}

	/*
	 * In new new installations (of the Sloeber development environment) the
	 * installer job will trigger downloads These mmust have finished before we can
	 * start testing
	 */

	public static void WaitForInstallerToFinish() {

		installAdditionalBoards();

		Shared.waitForAllJobsToFinish();
	}

	public static void installAdditionalBoards() {
		String[] packageUrlsToAdd = { "http://arduino.esp8266.com/stable/package_esp8266com_index.json",
				"https://raw.githubusercontent.com/stm32duino/BoardManagerFiles/master/STM32/package_stm_index.json" };
		BoardsManager.addPackageURLs(new HashSet<>(Arrays.asList(packageUrlsToAdd)), true);
		if (reinstall_boards_and_libraries) {
			BoardsManager.installAllLatestPlatforms();
		}
		BoardsManager.referenceLocallInstallation(Shared.getTeensyPlatform());

	}

	@Test
	public void testExamples() {
		IPath templateFolder = Shared.getTemplateFolder("fastBlink");
		CompileOptions compileOptions = new CompileOptions(null);
		compileOptions.setMyAditional_C_andCPP_CompileOptions("-DINTERVAL=" + interval);
		Build_Verify_upload(CodeDescriptor.createCustomTemplate(templateFolder), compileOptions);

	}

	public void Build_Verify_upload(CodeDescriptor codeDescriptor, CompileOptions compileOptions) {

		IProject theTestProject = null;

		NullProgressMonitor monitor = new NullProgressMonitor();
		String projectName = String.format("%05d_%s", new Integer(mCounter++), this.myName);
		try {

			theTestProject = this.myBoard.getBoardDescriptor().createProject(projectName, null,
					ConfigurationDescriptor.getDefaultDescriptors(), codeDescriptor, compileOptions, monitor);
			Shared.waitForAllJobsToFinish(); // for the indexer
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to create the project:" + projectName);
			return;
		}
		try {
			theTestProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
			if (Shared.hasBuildErrors(theTestProject)) {
				// try again because the libraries may not yet been added
				Shared.waitForAllJobsToFinish(); // for the indexer
				try {
					Thread.sleep(3000);// seen sometimes the libs were still not
										// added
				} catch (InterruptedException e) {
					// ignore
				}
				theTestProject.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
				if (Shared.hasBuildErrors(theTestProject)) {
					// give up
					fail("Failed to compile the project:" + projectName + " build errors");
				}
			}

		} catch (CoreException e) {
			e.printStackTrace();
			fail("Failed to compile the project:" + projectName + " exception");
		}
		Sketch.upload(theTestProject);
	}
}
