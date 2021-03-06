/*
 * Copyright (C) 2018 Veritas Technologies LLC.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fluidops.fedx.util;

import java.io.File;

import com.fluidops.fedx.Config;


/**
 * Utility function for files
 * 
 * @author Andreas Schwarte
 *
 */
public class FileUtil {

	/**
	 * location utility.<p>
	 * 
	 *  if the specified path is absolute, it is returned as is, 
	 *  otherwise a location relative to {@link Config#getBaseDir()} is returned<p>
	 *  
	 *  examples:<p>
	 *  
	 *  <code>
	 *  /home/data/myPath -> absolute linux path
	 *  c:\\data -> absolute windows path
	 *  \\\\myserver\data -> absolute windows network path (see {@link File#isAbsolute()})
	 *  data/myPath -> relative path (relative location to baseDir is returned)
	 *  </code>
	 *  
	 * @param path
	 * @return
	 * 			the file corresponding to the abstract path
	 */
	public static File getFileLocation(String path) {
		
		// check if path is an absolute path that already exists
		File f = new File(path);
		
		if (f.isAbsolute())
			return f;
		
		f = new File( Config.getConfig().getBaseDir() + path);
		return f;
	}
	
}
