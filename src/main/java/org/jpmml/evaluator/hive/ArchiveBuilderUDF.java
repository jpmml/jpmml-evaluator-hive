/*
 * Copyright (c) 2018 Villu Ruusmann
 *
 * This file is part of JPMML-Evaluator
 *
 * JPMML-Evaluator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Evaluator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Evaluator.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.evaluator.hive;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hive.ql.exec.UDF;

public class ArchiveBuilderUDF extends UDF {

	protected Log log = LogFactory.getLog(getClass());


	public ArchiveBuilderUDF(){
	}

	public String evaluate(String className, String pmmlFile, String udfJarFile){
		return evaluate(className, new File(pmmlFile), new File(udfJarFile));
	}

	public String evaluate(String className, File pmmlFile, File udfJarFile){

		try {
			CodeModelUtil.build(className, pmmlFile, udfJarFile);

			return udfJarFile.getAbsolutePath();
		} catch(Exception e){
			this.log.error("Failed to build", e);

			return null;
		}
	}
}