/*
 * Tomdroid
 * Tomboy on Android
 * http://www.launchpad.net/tomdroid
 *
 * This file is part of Tomdroid.
 *
 * Tomdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Tomdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Tomdroid.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.tomdroid;

import android.app.Activity;
import android.content.ContentResolver;
import android.database.Cursor;
import android.text.TextUtils;

import org.tomdroid.ui.Tomdroid;
import org.tomdroid.util.NewNote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Notebooks are represented exactly the way Tomboy's desktop Notebooks add-in represents
 * them, so that they keep working when synced back to Tomboy/grauphel: a note belongs to
 * a notebook by carrying a "system:notebook:<name>" tag, and each notebook additionally has
 * a hidden template note (tagged "system:template" + "system:notebook:<name>") that keeps
 * the notebook alive even when it has no member notes yet.
 */
public class NotebookManager {

	public static final String TAG_PREFIX = "system:notebook:";

	// sentinel passed around (never persisted) meaning "notes with no notebook"
	public static final String UNFILED = "system:notebook-filter:unfiled";

	public static String tagFor(String notebookName) {
		return TAG_PREFIX + notebookName;
	}

	// returns the notebook name a note belongs to, or null if it isn't in one
	public static String getNotebook(String tags) {
		if (TextUtils.isEmpty(tags))
			return null;
		for (String tag : tags.split(",")) {
			if (tag.startsWith(TAG_PREFIX))
				return tag.substring(TAG_PREFIX.length());
		}
		return null;
	}

	// removes any existing notebook tag from the note and assigns the given one (null/empty = no notebook)
	public static void setNotebook(Note note, String notebookName) {
		String tags = note.getTags();
		if (!TextUtils.isEmpty(tags)) {
			StringBuilder kept = new StringBuilder();
			for (String tag : tags.split(",")) {
				if (tag.length() == 0 || tag.startsWith(TAG_PREFIX))
					continue;
				if (kept.length() > 0)
					kept.append(",");
				kept.append(tag);
			}
			note.setTags(kept.toString());
		}
		if (!TextUtils.isEmpty(notebookName))
			note.addTag(tagFor(notebookName));
	}

	// returns the sorted list of distinct notebook names known locally
	public static List<String> getAllNotebooks(Activity activity) {
		ContentResolver cr = activity.getContentResolver();
		Cursor cursor = cr.query(Tomdroid.CONTENT_URI, new String[] { Note.TAGS },
				Note.TAGS + " LIKE ?", new String[] { "%" + TAG_PREFIX + "%" }, null);

		ArrayList<String> notebooks = new ArrayList<String>();
		if (cursor != null) {
			int col = cursor.getColumnIndexOrThrow(Note.TAGS);
			while (cursor.moveToNext()) {
				String notebook = getNotebook(cursor.getString(col));
				if (notebook != null && !notebooks.contains(notebook))
					notebooks.add(notebook);
			}
			cursor.close();
		}
		Collections.sort(notebooks, String.CASE_INSENSITIVE_ORDER);
		return notebooks;
	}

	// creates the notebook's hidden template note if the notebook doesn't already exist locally
	// (case-insensitively, matching getAllNotebooks()'s case-insensitive sort/dedup)
	public static void createNotebook(Activity activity, String notebookName) {
		for (String existing : getAllNotebooks(activity)) {
			if (existing.equalsIgnoreCase(notebookName))
				return;
		}

		Note template = NewNote.createNewNote(activity, notebookName + " Notebook Template", "");
		template.addTag("system:template");
		template.addTag(tagFor(notebookName));
		NoteManager.putNote(activity, template);
	}
}
