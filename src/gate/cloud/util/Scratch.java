/*
 *  Scratch.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: Scratch.java 17349 2014-02-19 18:02:24Z ian_roberts $ 
 */
package gate.cloud.util;

import static gate.cloud.io.IOConstants.PARAM_ARC_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_MIME_TYPES;
import gate.cloud.io.arc.ARCDocumentEnumerator;
import gate.cloud.io.arc.ArchiveDocumentEnumerator;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Class for trying out various things. NOT part of the GCP distro.
 */
public class Scratch {

	public static void main(String args[]) {
		try {
//			enumerateArcs(args);
			enumerateDocsInArc(args);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * Test code to enumerate all the arc files inside a directory and print out
	 * the number of documents they would match.
	 * 
	 * @param args
	 *            args[0] should be the directory containing the ARC files.
	 * @throws Exception
	 */
	private static void enumerateArcs(String[] args) throws Exception {
		Map<String, String> configData = new HashMap<String, String>();
		configData.put(PARAM_MIME_TYPES, "text/html application/pdf");

		File topDir = new File(args[0]);
		File arcs[] = topDir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".arc.gz") || name.endsWith(".arc");
			}
		});
		Arrays.sort(arcs, new Comparator<File>() {
			public int compare(File o1, File o2) {
				return o1.getAbsolutePath().compareTo(o2.getAbsolutePath());
			}
		});
		int arcCnt = 0;
		long totalDocs = 0;
		System.out.println("\"Archive\", \"Size (MBs)\", Documents, Total\n");
		for (File arcFile : arcs) {
			try {
				configData.put(PARAM_ARC_FILE_LOCATION,
						arcFile.getAbsolutePath());
				ArchiveDocumentEnumerator enumerator = new ARCDocumentEnumerator();
				enumerator.config(configData);
				enumerator.init();
				int docs = 0;
				while (enumerator.hasNext()) {
					enumerator.next();
					docs++;
				}
				totalDocs += docs;
				System.out.printf("\"%s\", %.4f, %d, %d\n",
				// name
						arcFile.getName(),
						// size (MBs)
						((double) arcFile.length() / 1048576),
						// included docs
						docs,
						// total docs so far
						totalDocs);
				arcCnt++;
			} catch (Exception e) {
				// report and ignore
				e.printStackTrace();
			}
		}
	}

	/**
	 * Test code to enumerate all the arc files inside a directory and print out
	 * the number of documents they would match.
	 * 
	 * @param args
	 *            args[0] should be the directory containing the ARC files.
	 * @throws Exception
	 */
	private static void enumerateDocsInArc(String[] args) throws Exception {
		Map<String, String> configData = new HashMap<String, String>();
		configData.put(PARAM_MIME_TYPES, "text/html application/pdf");

		File arcFile = new File(args[0]);
		try {
			configData.put(PARAM_ARC_FILE_LOCATION, arcFile.getAbsolutePath());
			ArchiveDocumentEnumerator enumerator = new ARCDocumentEnumerator();
			enumerator.config(configData);
			enumerator.init();
			int docs = 0;
			while (enumerator.hasNext()) {
				System.out.println(enumerator.next());
				docs++;
				if(docs > 200) return;
			}
		} catch (Exception e) {
			// report and ignore
			e.printStackTrace();
		}
	}

}
