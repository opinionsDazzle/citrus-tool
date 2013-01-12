package com.alibaba.eclipse.plugin.webx.util;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.utils.StringUtils;

@SuppressWarnings("restriction")
public class PluginUtil {
    private static final String JAR_FILE_PROTOCOL = "jar:file:"; //$NON-NLS-1$

    /**
     * 取得指定document所在的project。
     * <p/>
     * 参考实现：
     * {@link org.eclipse.jst.jsp.ui.internal.hyperlink.XMLJavaHyperlinkDetector#createHyperlink(String, IRegion, IDocument)}
     */
    public static IProject getProjectFromDocument(IDocument document) {
        // try file buffers
        ITextFileBuffer textFileBuffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(document);

        if (textFileBuffer != null) {
            IPath basePath = textFileBuffer.getLocation();

            if (basePath != null && !basePath.isEmpty()) {
                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(basePath.segment(0));

                if (basePath.segmentCount() > 1 && project.isAccessible()) {
                    return project;
                }
            }
        }

        // fallback to SSE-specific knowledge
        IStructuredModel model = null;

        try {
            model = StructuredModelManager.getModelManager().getExistingModelForRead(document);

            if (model != null) {
                String baseLocation = model.getBaseLocation();

                // URL fixup from the taglib index record
                if (baseLocation.startsWith("jar:/file:")) { //$NON-NLS-1$
                    baseLocation = StringUtils.replace(baseLocation, "jar:/", "jar:"); //$NON-NLS-1$ //$NON-NLS-2$
                }

                /*
                 * Handle opened TLD files from JARs on the Java Build Path by
                 * finding a package fragment root for the same .jar file and
                 * opening the class from there. Note that this might be from a
                 * different Java project's build path than the TLD.
                 */
                if (baseLocation.startsWith(JAR_FILE_PROTOCOL)
                        && baseLocation.indexOf('!') > JAR_FILE_PROTOCOL.length()) {
                    String baseFile = baseLocation.substring(JAR_FILE_PROTOCOL.length(), baseLocation.indexOf('!'));
                    IPath basePath = new Path(baseFile);
                    IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

                    for (int i = 0; i < projects.length; i++) {
                        try {
                            if (projects[i].isAccessible() && projects[i].hasNature(JavaCore.NATURE_ID)) {
                                IJavaProject javaProject = JavaCore.create(projects[i]);

                                if (javaProject.exists()) {
                                    IPackageFragmentRoot root = javaProject.findPackageFragmentRoot(basePath);

                                    if (root != null) {
                                        return javaProject.getProject();
                                    }
                                }
                            }
                        } catch (CoreException ignored) {
                        }
                    }
                } else {
                    IPath basePath = new Path(baseLocation);

                    if (basePath.segmentCount() > 1) {
                        return ResourcesPlugin.getWorkspace().getRoot().getProject(basePath.segment(0));
                    }
                }
            }
        } finally {
            if (model != null)
                model.releaseFromRead();
        }

        return null;
    }
}
