
package com.google.refine.commands.colfusion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.refine.ProjectManager;
import com.google.refine.commands.Command;
import com.google.refine.history.HistoryEntry;
import com.google.refine.io.FileProjectManager;
import com.google.refine.model.Project;
import com.google.refine.util.ParsingUtilities;

import edu.pitt.sis.exp.colfusion.dal.dataModels.tableDataModel.RelationKey;
import edu.pitt.sis.exp.colfusion.dal.databaseHandlers.DatabaseHandlerFactory;
import edu.pitt.sis.exp.colfusion.dal.databaseHandlers.MetadataDbHandler;
import edu.pitt.sis.exp.colfusion.dal.managers.ColumnTableInfoManager;
import edu.pitt.sis.exp.colfusion.dal.managers.ColumnTableInfoManagerImpl;
import edu.pitt.sis.exp.colfusion.dal.orm.ColfusionColumnTableInfo;
//import edu.pitt.sis.exp.colfusion.dal.databaseHandlers.TargetDatabaseHandlerFactory;
import edu.pitt.sis.exp.colfusion.utils.ConfigManager;
import edu.pitt.sis.exp.colfusion.utils.PropertyKeys;


public class SetCheckPointCommand extends Command {

	@Override
	public void doGet(final HttpServletRequest request, final HttpServletResponse response)
			throws IOException, ServletException {

		final Properties parameters = ParsingUtilities.parseUrlParameters(request);

		final MetadataDbHandler metadataDbHandler = DatabaseHandlerFactory.getMetadataDbHandler();

		final String url = parameters.getProperty("url");
		final Long projectId = Long.valueOf(getProjectId(url));
		int sid = -1;
		RelationKey relationKey = null;
		try {
			final Project project = ProjectManager.singleton.getProject(projectId);
			sid = metadataDbHandler.getSid(String.valueOf(projectId));

			final ColumnTableInfoManager columnTableMng = new ColumnTableInfoManagerImpl();
			final ColfusionColumnTableInfo columnTable = columnTableMng.findBySidAndOriginalTableName(sid, project.getMetadata().getName());
			relationKey = new RelationKey(project.getMetadata().getName(), columnTable.getDbTableName());
		} catch (final SQLException e1) {
			e1.printStackTrace();
		}

		final Project project = FileProjectManager.singleton.getProject(projectId);
		final int historyEntrySize = project.history.getLastPastEntries(0).size();

		final ConfigManager configMng = ConfigManager.getInstance();

		String dir = configMng.getProperty(PropertyKeys.COLFUSION_OPENREFINE_FOLDER);
		final String tempFolder = configMng.getProperty(PropertyKeys.COLFUSION_OPENREFINE_FOLDER_TEMP);

		if (dir != null && !dir.endsWith(File.separator)) {
			dir += File.separator;
		}

		final String tempDir = dir + tempFolder + File.separator;
		final String projectDir = projectId + ".project" + File.separator;

		if (!(new File(tempDir).isDirectory())) {
			new File(tempDir).mkdir();
		}

		if (!(new File(tempDir + projectDir).isDirectory())) {
			// If no copied project in temp folder, copy it to temp folder
			copyFolder(dir + projectDir, tempDir + projectDir);
			if (!(new File(tempDir + projectDir + "history").isDirectory())) {
				new File(tempDir + projectDir + "history").mkdir();
			}
		} else {
			/*
			 * If copied project exists in temp folder, then check if they have the
			 * same history, if not, roll back to temp folder's status, because temp
			 * folder's history exist only if user click "Save" button
			 */
			final File savedHistory = new File(tempDir + projectDir + "history");
			final String[] savedChanges = savedHistory.list();
			int savedChangesLength = 0;
			if(savedHistory.list() != null) {
				savedChangesLength = savedChanges.length;
			}

			final File notSavedHistory = new File(dir + projectDir + "history");
			final String[] notSavedChanges = notSavedHistory.list();
			int notSavedChangesLength = 0;
			if(notSavedHistory.list() != null) {
				notSavedChangesLength = notSavedChanges.length;
			}

			final int undoCount = notSavedChangesLength - savedChangesLength;

			if (undoCount > 0) {
				if (historyEntrySize - 1 - undoCount < 0) {
					project.history.undoRedo(Long.valueOf("0"));
				} else if(savedChanges.length != historyEntrySize) {
					System.out.println("****************");
					System.out.println("savedChanges.length: " + savedChanges.length);
					System.out.println("historyEntrySize: " + historyEntrySize);
					System.out.println("****************");
					// If undoCount is "1", it means undo only the last change
					// If undoCount is "2", should undo last two changes
					final Long lastDoneEntryID = project.history.getLastPastEntries(0).get(historyEntrySize - 1 - undoCount).id;
					project.history.undoRedo(lastDoneEntryID);
				}
			} else if (undoCount == 0) {
				int count = -1;
				int isSaved = -1;
				try {
					count = metadataDbHandler.getCountFromOpenRefineHistoryHelper(sid, relationKey);
					isSaved = metadataDbHandler.getIsSavedFromOpenRefineHistoryHelper(sid, relationKey);
				} catch (final SQLException e) {
					e.printStackTrace();
				}

				if(isSaved != 1) {

					//                } else if(count == 1 && isSaved == 0) {

				} else {

					if (historyEntrySize >=1) {

						final HistoryEntry lastHistoryEntry = project.history.getLastPastEntries(0).get(historyEntrySize - 1);
						final Long lastHistoryEntryId = project.history.getLastPastEntries(0).get(historyEntrySize - 1).id;

						if (historyEntrySize == 1) {
							project.history.undoRedo(0); // this will roll back to the project creation.
							project.history.addEntry(lastHistoryEntry);
						}
						else {
							final Long lastDoneEntryID = project.history.getLastPastEntries(0).get(historyEntrySize - 2).id;
							project.history.undoRedo(lastDoneEntryID);
							project.history.addEntry(lastHistoryEntry);
						}

						copyFile(tempDir + projectDir + "history" + File.separator + lastHistoryEntryId + ".change.zip", dir + projectDir + "history" + File.separator + lastHistoryEntryId + ".change.zip");

						final File folderPath = new File(tempDir + projectDir);
						deleteAllFilesOfDir(folderPath);
						copyFolder(dir + projectDir, tempDir + projectDir);

					}
				}
			}
		}

		final JSONObject result = new JSONObject();

		try {

			result.put("successful", true);
			result.put("testMsg", getProjectId(url) + "hello!!!" + url);
		} catch (final JSONException e) {
			e.printStackTrace();
		}

		response.setCharacterEncoding("UTF-8");
		response.setHeader("Content-Type", "application/json");
		respond(response, result.toString());
	}

	private String getProjectId(final String url) {
		// Assumes that the url has this format: ...?project=1626526417268
		return url.substring(url.indexOf('=') + 1);
	}

	private void copyFile(final String sourceFile, final String targetFile) {
		try {
			int byteread = 0;
			final File oldfile = new File(sourceFile);
			if (oldfile.exists()) {
				final InputStream inStream = new FileInputStream(sourceFile);
				@SuppressWarnings("resource")
				final FileOutputStream fs = new FileOutputStream(targetFile);
				final byte[] buffer = new byte[1444];
				while ((byteread = inStream.read(buffer)) != -1) {
					fs.write(buffer, 0, byteread);
				}
				inStream.close();
			}
		} catch (final Exception e) {
			System.out.println("Error happens when copying files");
			e.printStackTrace();
		}
	}

	private void copyFolder(final String sourceDir, final String targetDir) {
		try {
			(new File(targetDir)).mkdirs();
			final File a = new File(sourceDir);
			final String[] file = a.list();
			File temp = null;
			for (int i = 0; i < file.length; i++) {
				if (sourceDir.endsWith(File.separator)) {
					temp = new File(sourceDir + file[i]);
				} else {
					temp = new File(sourceDir + File.separator + file[i]);
				}

				if (temp.isFile()) {
					final FileInputStream input = new FileInputStream(temp);
					final FileOutputStream output = new FileOutputStream(targetDir + "/" + (temp.getName()).toString());
					final byte[] b = new byte[1024 * 5];
					int len;
					while ((len = input.read(b)) != -1) {
						output.write(b, 0, len);
					}
					output.flush();
					output.close();
					input.close();
				}
				if (temp.isDirectory()) {
					copyFolder(sourceDir + "/" + file[i], targetDir + "/" + file[i]);
				}
			}
		} catch (final Exception e) {
			System.out.println("Error happens when copying files in folder");
			e.printStackTrace();

		}
	}

	private void copyZipFile(final String sourceZipFile, final String targetZipFile) {
		InputStream inStream = null;
		try {
			inStream = new FileInputStream(sourceZipFile);
		} catch (final FileNotFoundException e) {
			System.err.println("[" + sourceZipFile + "]" + "\r\n" + e.getCause());
			return;
		}
		final File targetFile = new File(targetZipFile);
		OutputStream outStream = null;
		try {
			targetFile.createNewFile();
			outStream = new FileOutputStream(targetFile);
			final byte[] by = new byte[1024];
			while (inStream.available() > 0) {
				inStream.read(by);
				outStream.write(by);
			}
		} catch (final IOException e) {
			System.err.println("Error happens when create file [" + targetZipFile + "]" + "\r\n" + e.getCause());
		} finally {
			if (null != inStream) {
				try {
					inStream.close();
				} catch (final IOException e) {
					System.err.println(e.getCause());
				}
			}
			if (null != outStream) {
				try {
					outStream.flush();
				} catch (final IOException e) {
					System.err.println(e.getCause());
				}
				try {
					outStream.close();
				} catch (final IOException e) {
					System.err.println(e.getCause());
				}
			}
		}
	}

	public void deleteAllFilesOfDir(final File path) {
		if (!path.exists()) {
			return;
		}
		if (path.isFile()) {
			path.delete();
			return;
		}
		final File[] files = path.listFiles();
		for (int i = 0; i < files.length; i++) {
			deleteAllFilesOfDir(files[i]);
		}
		path.delete();
	}
}
