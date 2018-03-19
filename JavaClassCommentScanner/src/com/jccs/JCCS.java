/*
 * Copyright 2018 Jonathan West
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.jccs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Stack;

/**
 * This simple utility is used to scan Java source files for class-level comments. It uses simple string parsing, rather than a Java source parser, to identify comments.
 * 
 * A class-level comments is one that occurs on the main class of a Java file. For example, this comment is class-level documentation of the JCCS class.
 * 
 *  A class/interface is considered to have a class-level source comment, as such:
 *  - Begin searching for 'class' 'public' or 'interface' after the first 'package' or 'import' statement is seen:
 *  	o A line is considered to be a comment if it begins with // or / *  
 *  	o Classes/interfaces annotated with @Deprecated are ignored.
 *  - If the strings 'class' 'public' or 'interface' are seen at the beginning of a line before a comment is seen, the class is considered not to have a source level comment.
 * 
 * The purposes of this algorithm is to identify class-level documentation, but not to misidentify copyright statements as documentation. Copyright statements
 * are traditionally before the 'package' and 'import' keywords.
 * 
 * On completion, a report will be output to the console to indicate classes with missing class-level documentation.
 * 
 */
public class JCCS {

	public static void main(String[] args) {
		
		if(args.length != 1) {
			System.err.println("Missing argument: (path to scan)");
			return;
		}
		
		System.out.println("* Beginning scan of "+args[0]);
		
		File root = new File(args[0]);
		
		try {
			scanFiles(root);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	private static void scanFiles(File root) throws IOException {

		Context c = new Context();
		
		Stack<File> s = new Stack<File>();
		s.push(root);
		
		while(s.size() > 0) {
			
			File currDir = s.pop();
			
			File[] files = currDir.listFiles();
			if(files == null) {continue; }
			
			for(File curr : files) {
				
				if(curr.isDirectory()) { s.push(curr); }
				else {
					processFile2(curr, c);
				}
			}
		}

		// Calculate the % and strip decimal places
		String percent = Double.toString(100d*c.numFilesWithError/c.filesProcessed);
		if(percent.contains(".")) {
			percent = percent.substring(0, percent.indexOf("."));
		}
		
		// Result
		System.out.println();
		System.out.println("Complete. Fail: "+c.numFilesWithError+"/"+c.filesProcessed+ " ("+percent+"%)");
		

	}

	
	private static void processFile(File f, Context c) throws IOException {
		
		// Java-only
		if(!f.getPath().toLowerCase().endsWith(".java")) {
			return;
		}
		
		c.filesProcessed++;
		
		BufferedReader br = new BufferedReader(new FileReader(f));
	
		boolean packageSeen = false;
		boolean firstImportSeen = false;
		
		boolean fail = false;
		
		try {
			String str;
			outer: while(null != (str = br.readLine())) {
				
				String lcaseTrim = str.trim().toLowerCase();
				
				if(lcaseTrim.startsWith("package ")) {
					packageSeen = true;
					
				} else if(lcaseTrim.startsWith("import")) {
					firstImportSeen = true;
					
				} else if(lcaseTrim.startsWith("@deprecated")) {
					// Ignore deprecated classes.
					return;
					
				} else if(firstImportSeen || packageSeen) {
					
					if(lcaseTrim.startsWith("class ") || lcaseTrim.startsWith("public ") || lcaseTrim.startsWith("interface ")) {
						fail = true;
						break outer;
					}
					
					if(lcaseTrim.startsWith("/*") || lcaseTrim.startsWith("//")) {
						return;
					}
					
				}
				
			}
			
			if(fail) {
				c.numFilesWithError++;
				System.err.println( c.numFilesWithError+") No comment found: "+f.getPath());
			}
		
		} finally {

			br.close();
		}
		
	}

	
	enum ProcessFileState { MULTI_LINE, COMMENT_LAST, OUTSIDE };
	
	private static void processFile2(File f, Context c) throws IOException {
		
		// Java-only
		if(!f.getPath().toLowerCase().endsWith(".java")) {
			return;
		}
		
		c.filesProcessed++;
		
		BufferedReader br = new BufferedReader(new FileReader(f));
	
		
		ProcessFileState state = ProcessFileState.OUTSIDE;

		boolean fileContainsErrors = false;
		
		
		int lineNumber = 1;
		try {
			String str;
			while(null != (str = br.readLine())) {
				boolean fail = false;
				
				String lcaseTrim = str.trim().toLowerCase();
				
				String lcaseTrimNoSpacesNoKeywords = lcaseTrim.replace(" ", "");
				lcaseTrimNoSpacesNoKeywords = lcaseTrimNoSpacesNoKeywords.replace("static", "");
				lcaseTrimNoSpacesNoKeywords = lcaseTrimNoSpacesNoKeywords.replace("abstract", "");
				lcaseTrimNoSpacesNoKeywords = lcaseTrimNoSpacesNoKeywords.replace("final", "");
				lcaseTrimNoSpacesNoKeywords = lcaseTrimNoSpacesNoKeywords.replace("public", "");
				lcaseTrimNoSpacesNoKeywords = lcaseTrimNoSpacesNoKeywords.replace("private", "");
				lcaseTrimNoSpacesNoKeywords = lcaseTrimNoSpacesNoKeywords.replace("protected", "");
				
//				System.out.println(lineNumber+" ) "+state.name()+" " +lcaseTrim);
				
				if(state == ProcessFileState.OUTSIDE) {
					if(
							(lcaseTrimNoSpacesNoKeywords.startsWith("class") || lcaseTrimNoSpacesNoKeywords.startsWith("interface"))
							
							// Ignore methods that return class: public Class<?> getSource() { }
							&& !lcaseTrimNoSpacesNoKeywords.contains("()")
						) {
						fail = true;
						fileContainsErrors = true;
					}
					
				}
				
				if(state == ProcessFileState.COMMENT_LAST) {
					if(lcaseTrim.length() > 0 && !lcaseTrim.startsWith("//") && !lcaseTrim.startsWith("/*") 
							&& !lcaseTrim.startsWith("@")) {
						state = ProcessFileState.OUTSIDE;
					}
				}
				
				
				if(state == ProcessFileState.OUTSIDE || state == ProcessFileState.COMMENT_LAST) {
					if(lcaseTrim.startsWith("/*")) {
						state = ProcessFileState.MULTI_LINE;

					} else if(lcaseTrim.startsWith("//")) {
						state = ProcessFileState.COMMENT_LAST;
						
					} else if(lcaseTrim.startsWith("@deprecated")) { 
						// Ignore classes that deprecated.
						state = ProcessFileState.COMMENT_LAST;
					}
										
				}
				
				if(state == ProcessFileState.MULTI_LINE) {
					if(lcaseTrim.endsWith("*/")) {
						state = ProcessFileState.COMMENT_LAST;
					}					
				}

				if(fail) {
					c.numErrorsNew++;
					System.err.println( c.numErrorsNew+") No comment found: "+f.getPath()+ ": "+lineNumber);
					fail = false;
				}
				lineNumber++;

			}
			
		
		} finally {

			if(fileContainsErrors) {
				c.numFilesWithError++;
			}
			
			br.close();
		}
		
	}

	
	private static class Context {
		long numErrorsNew = 0;
		
		long numFilesWithError = 0;
		
		
		
		long filesProcessed = 0;
	}

}

