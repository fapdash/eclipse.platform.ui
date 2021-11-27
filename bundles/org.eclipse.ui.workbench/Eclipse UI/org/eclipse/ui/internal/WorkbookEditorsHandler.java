/*******************************************************************************
 * Copyright (c) 2007, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Marc-Andre Laperle (Ericsson) - Bug 413278
 *     Patrik Suzzi <psuzzi@gmail.com> - Bug 497618, 368977, 504088, 506019, 486859, 552144
 ******************************************************************************/

package org.eclipse.ui.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.renderers.swt.StackRenderer;
import org.eclipse.jface.viewers.BoldStylerProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Font;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPathEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.dialogs.SearchPattern;
import org.eclipse.ui.dialogs.StyledStringHighlighter;
import org.eclipse.ui.themes.ITheme;

/**
 * Shows a list of open editor and parts in the current or last active workbook.
 *
 * @since 3.4
 *
 */
public class WorkbookEditorsHandler extends FilteredTableBaseHandler {

	/**
	 * Preference node for the workbench SWT renderer
	 */
	private static final String ORG_ECLIPSE_E4_UI_WORKBENCH_RENDERERS_SWT = "org.eclipse.e4.ui.workbench.renderers.swt"; //$NON-NLS-1$

	/**
	 * Id for the command that opens the editor drop down
	 */
	private static final String ORG_ECLIPSE_UI_WINDOW_OPEN_EDITOR_DROP_DOWN = "org.eclipse.ui.window.openEditorDropDown"; //$NON-NLS-1$

	/**
	 * E4 Tag used to identify the active part
	 */
	private static final String TAG_ACTIVE = "active"; //$NON-NLS-1$

	private SearchPattern searchPattern;

	private Map<EditorReference, String> editorReferenceColumnLabelTexts;

	/**
	 * Gets the preference "show most recently used tabs" (MRU tabs)
	 *
	 * @return Returns the enableMRU.
	 */
	private static boolean isMruEnabled() {
		IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(ORG_ECLIPSE_E4_UI_WORKBENCH_RENDERERS_SWT);
		boolean initialMRUValue = preferences.getBoolean(StackRenderer.MRU_KEY_DEFAULT, StackRenderer.MRU_DEFAULT);
		return preferences.getBoolean(StackRenderer.MRU_KEY, initialMRUValue);
	}

	@Override
	protected Object getInput(WorkbenchPage page) {
		List<EditorReference> editorReferences = getParts(page);
		editorReferenceColumnLabelTexts = generateColumnLabelTexts(editorReferences);
		return editorReferences;
	}

	private List<EditorReference> getParts(WorkbenchPage page) {
		List<EditorReference> refs;
		if (isMruEnabled()) {
			// sorted, MRU order
			refs = page.getSortedEditorReferences();
		} else {
			// non sorted, First Opened order
			refs = new ArrayList<>();
			for (IEditorReference ier : page.getEditorReferences()) {
				refs.add((EditorReference) ier);
			}
		}
		return refs;
	}

	/**
	 * Generates a mapping of EditorReferences to display label texts. If display
	 * names collide parent directories will be added until the EditorReference can
	 * be differentiated from all references that share the same file name. If the
	 * collisions share multiple paths segments only the first differing path
	 * segment will be prepended.<br>
	 * <br>
	 * Example where all collisions share the same segments:
	 *
	 * <pre>
	 * /project/test1/foo/bar/file -> test1/file
	 * /project/test2/foo/bar/file -> test2/file
	 * /project/test3/foo/bar/file -> test3/file
	 * </pre>
	 *
	 * Example with differing segments:
	 *
	 * <pre>
	 * /project/test1/foo/bar/file -> test1/foo/bar/file
	 * /project/test2/foo/bar/file -> test2/foo/bar/file
	 * /project/file               -> project/file
	 * </pre>
	 *
	 * @param editorReferences the references for which the display label should be
	 *                         generated
	 * @return Mapping of EditorReferences to their display label
	 */
	private Map<EditorReference, String> generateColumnLabelTexts(List<EditorReference> editorReferences) {
		Map<EditorReference, String> editorReferenceLabelTexts = new HashMap<>(editorReferences.size());
		Map<String, List<Entry<EditorReference, IPath>>> collisionsMap = new HashMap<>(editorReferences.size());
		editorReferences.forEach(editorReference -> {
			try {
				IPathEditorInput iPathEditorInput = getPathEditorInput(editorReference.getEditorInput());
				if (iPathEditorInput != null && iPathEditorInput.getPath() != null) {
					IPath path = iPathEditorInput.getPath();

					List<Entry<EditorReference, IPath>> referencesWithSameTitle = collisionsMap
							.get(editorReference.getTitle());
					if (referencesWithSameTitle == null) {
						referencesWithSameTitle = new ArrayList<>();
						collisionsMap.put(editorReference.getTitle(), referencesWithSameTitle);
					}

					referencesWithSameTitle.add(Map.entry(editorReference, path));
				} else {
					// we only detect collisions for IPathEditorInput
					editorReferenceLabelTexts.put(editorReference, getWorkbenchPartReferenceText(editorReference));
				}
			} catch (PartInitException e) {
				// This should never happen as all the parts are initialized?
				String message = "Expected parts to be initialized"; //$NON-NLS-1$
				final IStatus status = new Status(IStatus.ERROR, WorkbenchPlugin.PI_WORKBENCH, 0, message, e);
				WorkbenchPlugin.log(message, status);
			}
		});

		for (List<Entry<EditorReference, IPath>> groupedEditorReferences : collisionsMap.values()) {
			if (groupedEditorReferences.size() == 1) {
				EditorReference editorReference = groupedEditorReferences.get(0).getKey();
				editorReferenceLabelTexts.put(editorReference, getWorkbenchPartReferenceText(editorReference));
			} else {
				Set<Integer> differingMaxSegmentsCounter = new HashSet<>();
				List<Integer> maxMatchingSegmentsList = new ArrayList<>(groupedEditorReferences.size());
				for (Entry<EditorReference, IPath> entry : groupedEditorReferences) {
					IPath path = entry.getValue();
					int maxMatchingSegments = -1;
					for (int i = 0; i < groupedEditorReferences.size(); i++) {
						IPath currentPath = groupedEditorReferences.get(i).getValue();
						if (currentPath.equals(path)) {
							continue;
						}
						int currentMatchingSegments = matchingLastSegments(path, currentPath);
						maxMatchingSegments = maxMatchingSegments < currentMatchingSegments ? currentMatchingSegments
								: maxMatchingSegments;
					}
					differingMaxSegmentsCounter.add(maxMatchingSegments);
					maxMatchingSegmentsList.add(maxMatchingSegments);
				}

				for (int i = 0; i < maxMatchingSegmentsList.size(); i++) {
					EditorReference editorReference = groupedEditorReferences.get(i).getKey();
					Integer maxMatchingSegment = maxMatchingSegmentsList.get(i);
					IPath path = groupedEditorReferences.get(i).getValue();

					String labelText = generateLabelText(editorReference, path, differingMaxSegmentsCounter,
							maxMatchingSegment);
					editorReferenceLabelTexts.put(editorReference, labelText);
				}
			}
		}
		return editorReferenceLabelTexts;
	}

	private IPathEditorInput getPathEditorInput(IEditorInput input) {
		if (input instanceof IPathEditorInput) {
			return (IPathEditorInput) input;
		}
		return Adapters.adapt(input, IPathEditorInput.class);
	}

	/**
	 * Generates the display text for the editor reference.
	 *
	 * @param matchingSegmentCounter contains all unique max segment numbers
	 * @param maxMatchingSegment     the maximal amount of sections this reference
	 *                               shares with a conflicting reference
	 * @param path                   path of the editorReference
	 * @return the final label text for the editor reference
	 */
	private String generateLabelText(EditorReference editorReference, IPath path,
			Set<Integer> differingMaxSegmentsCounter, Integer maxMatchingSegment) {
		String labelText;
		if (differingMaxSegmentsCounter.size() == 1) {
			String lastSegment = path.lastSegment();
			labelText = Path.fromPortableString(path.segment(path.segmentCount() - 1 - maxMatchingSegment))
					.append(lastSegment).toOSString();
		} else {
			labelText = path.removeFirstSegments(path.segmentCount() - 1 - maxMatchingSegment).toOSString();
		}
		return prependDirtyIndicationIfDirty(editorReference, labelText);
	}

	/**
	 * Prepends a {@code *} to the labelText if editorReference is dirty.
	 *
	 * @param editorReference reference to check for dirty state
	 * @param labelText       the label text for the editorReference
	 * @return text with dirty indication when appropriate
	 */
	private String prependDirtyIndicationIfDirty(EditorReference editorReference, String labelText) {
		if (editorReference.isDirty()) {
			return "*" + labelText; //$NON-NLS-1$
		}
		return labelText;
	}

	/**
	 * Returns a count of the number of segments which match in this path and the
	 * given path (device ids are ignored), comparing in decreasing segment number
	 * order starting at the last segment.
	 *
	 * @param path
	 * @param anotherPath the other path to compare with
	 * @return the number of matching segments
	 */
	private int matchingLastSegments(IPath path, IPath anotherPath) {
		int thisPathLen = path.segmentCount();
		int anotherPathLen = anotherPath.segmentCount();
		int max = Math.min(thisPathLen, anotherPathLen);
		int count = 0;
		for (int i = 1; i <= max; i++) {
			if (!path.segment(thisPathLen - i).equals(anotherPath.segment(anotherPathLen - i))) {
				return count;
			}
			count++;
		}
		return count;
	}

	@Override
	protected boolean isFiltered() {
		return true;
	}

	SearchPattern getMatcher() {
		return searchPattern;
	}

	@Override
	protected void setMatcherString(String pattern) {
		if (pattern.isEmpty()) {
			searchPattern = null;
		} else {
			searchPattern = new SearchPattern();
			searchPattern.setPattern(pattern);
		}
	}

	/**
	 * Specializes
	 * {@link FilteredTableBaseHandler#setLabelProvider(TableViewerColumn)} by
	 * providing custom styles to the table cells
	 */
	@Override
	protected void setLabelProvider(final TableViewerColumn tableViewerColumn) {

		tableViewerColumn.setLabelProvider(new StyledCellLabelProvider() {

			/* called once for each element in the table */
			@Override
			public void update(ViewerCell cell) {
				Object element = cell.getElement();
				if (element instanceof WorkbenchPartReference) {
					WorkbenchPartReference ref = (WorkbenchPartReference) element;
					String text = editorReferenceColumnLabelTexts.get(ref);
					cell.setText(text);
					cell.setImage(ref.getTitleImage());

					SearchPattern matcher = WorkbookEditorsHandler.this.getMatcher();
					if (matcher == null) {
						cell.setStyleRanges(null);
					} else {
						String pattern = matcher.getPattern();
						StyledStringHighlighter ssh = new StyledStringHighlighter();
						StyledString ss = ssh.highlight(text, pattern,
								new BoldStylerProvider(WorkbookEditorsHandler.this.getFont(false, true))
										.getBoldStyler());
						cell.setStyleRanges(ss.getStyleRanges());
					}

					cell.getControl().redraw();
				}
			}

			@Override
			public String getToolTipText(Object element) {
				if (element instanceof WorkbenchPartReference) {
					WorkbenchPartReference ref = (WorkbenchPartReference) element;
					return ref.getTitleToolTip();
				}
				return super.getToolTipText(element);
			}

		});

		ColumnViewerToolTipSupport.enableFor(tableViewerColumn.getViewer());
	}

	/** True if the given model represents the active editor */
	protected boolean isActiveEditor(MPart model) {
		if (model == null || model.getTags() == null) {
			return false;
		}
		return model.getTags().contains(TAG_ACTIVE);
	}

	/** True is the given model represents an hidden editor */
	protected boolean isHiddenEditor(MPart model) {
		if (model == null || model.getParent() == null || !(model.getParent().getRenderer() instanceof StackRenderer)) {
			return false;
		}
		StackRenderer renderer = (StackRenderer) model.getParent().getRenderer();
		CTabItem item = renderer.findItemForPart(model);
		return (item != null && !item.isShowing());
	}

	private Font getFont(boolean hidden, boolean active) {
		ITheme theme = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
		if (active) {
			return theme.getFontRegistry().getItalic(IWorkbenchThemeConstants.TAB_TEXT_FONT);
		}
		if (hidden) {
			return theme.getFontRegistry().getBold(IWorkbenchThemeConstants.TAB_TEXT_FONT);
		}
		return theme.getFontRegistry().get(IWorkbenchThemeConstants.TAB_TEXT_FONT);
	}

	@Override
	protected ViewerFilter getFilter() {
		return new ViewerFilter() {
			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element) {
				SearchPattern matcher = getMatcher();
				if (matcher == null || !(viewer instanceof TableViewer)) {
					return true;
				}
				String matchName = null;
				if (element instanceof EditorReference) {
					matchName = ((EditorReference) element).getTitle();
					// skips dirty editor prefix
					if (matchName.startsWith("*")) { //$NON-NLS-1$
						matchName = matchName.substring(1);
					}
				}
				if (matchName == null) {
					return false;
				}
				return matcher.matches(matchName);
			}
		};
	}

	@Override
	protected ParameterizedCommand getBackwardCommand() {
		return null;
	}

	@Override
	protected ParameterizedCommand getForwardCommand() {
		final ICommandService commandService = window.getWorkbench().getService(ICommandService.class);
		final Command command = commandService.getCommand(ORG_ECLIPSE_UI_WINDOW_OPEN_EDITOR_DROP_DOWN);
		return new ParameterizedCommand(command, null);
	}

	@Override
	protected int getCurrentItemIndex() {
		if (isMruEnabled()) {
			return 0;
		}
		// We need to find previously selected part and return the index of the
		// part before this part in our list, which ordered not by use but by
		// position

		WorkbenchPage page = (WorkbenchPage) window.getActivePage();
		List<EditorReference> sortedByUse = page.getSortedEditorReferences();
		if (sortedByUse.size() < 2) {
			return 0;
		}

		// this is the previously used editor
		EditorReference next = sortedByUse.get(1);

		// now let's find it position in our list
		List<EditorReference> sortedByPosition = getParts(page);
		for (int i = 0; i < sortedByPosition.size(); i++) {
			EditorReference ref = sortedByPosition.get(i);
			if (ref == next) {
				if (i > 0) {
					// return the position of the previous part in our list
					gotoDirection = true;
					return i - 1;
				}
				// if the previous part is the *first* one in our list,
				// we should invert the traversal direction to "back".
				gotoDirection = false;
				return 1;
			}
		}
		return super.getCurrentItemIndex();
	}

}
