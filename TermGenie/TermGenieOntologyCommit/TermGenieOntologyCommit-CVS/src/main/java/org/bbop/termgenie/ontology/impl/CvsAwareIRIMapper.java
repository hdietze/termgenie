package org.bbop.termgenie.ontology.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.bbop.termgenie.cvs.CvsTools;
import org.bbop.termgenie.ontology.IRIMapper;

import com.google.inject.Singleton;

/**
 * {@link IRIMapper} which maps files from a cvs repository.<br/>
 * Uses a fall-back {@link IRIMapper} to map unknown IRIs.
 * <br/>
 * WARNING: Do not create multiple instances with the same workFolder. May
 * lead to strange side effects, as the wokrFolder is cleared in the setup process.
 */
@Singleton
public class CvsAwareIRIMapper extends AbstractScmAwareIRIMapper<CvsAwareIRIMapper.CvsHandler> {

	/**
	 * @param fallBackIRIMapper
	 * @param cvsRoot
	 * @param cvsPassword
	 * @param workFolder
	 * @param mappedCVSFiles
	 * @param checkout
	 */
	// Do not provide an IOC constructor, as there are multiple implementations
	// use @Provides methods instead
	public CvsAwareIRIMapper(IRIMapper fallBackIRIMapper,
			String cvsRoot,
			String cvsPassword,
			File workFolder,
			Map<String, String> mappedCVSFiles,
			String checkout)
	{
		super(fallBackIRIMapper, new CvsHandler(cvsRoot, cvsPassword, workFolder, mappedCVSFiles, checkout));
	}

	static class CvsHandler implements AbstractScmAwareIRIMapper.ReadOnlyScm {

		private final CvsTools cvs;
		private final Map<String, String> mappedCVSFiles;

		CvsHandler(String cvsRoot,
				String cvsPassword,
				File targetFolder,
				Map<String, String> mappedCVSFiles,
				String checkout)
		{
			this.mappedCVSFiles = mappedCVSFiles;
			cvs = new CvsTools(cvsRoot, cvsPassword, targetFolder);
			try {
				// create work directory
				targetFolder.mkdirs();
				// always clean the work directory.
				FileUtils.cleanDirectory(targetFolder);
				cvs.connect();
				cvs.checkout(Collections.singletonList(checkout));
			} catch (IOException exception) {
				throw new RuntimeException(exception);
			}
			finally {
				try {
					cvs.close();
				} catch (IOException exception) {
					Logger.getLogger(getClass()).warn("Could not close cvs connection.", exception);
				}
			}
		}

		@Override
		public void update(String url) throws IOException {
			String cvsFile = mappedCVSFiles.get(url);
			try {
				cvs.connect();
				cvs.update(Collections.singletonList(cvsFile));
			} catch (IOException exception) {
				throw exception;
			}
			finally {
				try {
					cvs.close();
				} catch (Exception exception) {
					Logger.getLogger(getClass()).warn("Could not close cvs connection.", exception);
				}
			}
		}

		@Override
		public File getFile(String url) {
			String cvsFile = mappedCVSFiles.get(url);
			return new File(cvs.getTargetFolder(), cvsFile);
		}
	}

	@Override
	protected boolean isScmMapped(String url, CvsHandler cvs) {
		return cvs.mappedCVSFiles.containsKey(url);
	}

}
