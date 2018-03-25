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
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPackage;
import org.jpmml.codemodel.ArchiverUtil;
import org.jpmml.codemodel.CompilerUtil;
import org.jpmml.codemodel.JDirectFile;
import org.jpmml.codemodel.JServiceConfigurationFile;

public class CodeModelUtil {

	private CodeModelUtil(){
	}

	static
	public void build(String className, File pmmlFile, File udfJarFile) throws Exception {
		JCodeModel codeModel = new JCodeModel();

		JClass evaluatorUdfClass = codeModel.ref(EvaluatorUDF.class);

		JDefinedClass definedEvaluatorUdfClass = codeModel._class(JMod.PUBLIC, className, ClassType.CLASS);
		definedEvaluatorUdfClass._extends(evaluatorUdfClass);

		JClass archiveResourceClass = codeModel.anonymousClass(ArchiveResource.class);

		JExpression newArchiveResource = JExpr._new(archiveResourceClass).arg(JExpr.lit("/" + pmmlFile.getName()));

		JMethod defaultConstructor = definedEvaluatorUdfClass.constructor(JMod.PUBLIC);

		JBlock constructorBody = defaultConstructor.body();
		constructorBody.add(JExpr.invoke("super").arg(newArchiveResource));

		JMethod getFuncNameMethod = definedEvaluatorUdfClass.method(JMod.PUBLIC, String.class, "getFuncName");
		getFuncNameMethod.annotate(Override.class);

		JBlock methodBody = getFuncNameMethod.body();
		methodBody._return(JExpr.lit(definedEvaluatorUdfClass.name()));

		CompilerUtil.compile(codeModel);

		JPackage rootPackage = codeModel.rootPackage();
		rootPackage.addResourceFile(new JDirectFile(pmmlFile.getName(), pmmlFile));

		JPackage servicePackage = codeModel._package("META-INF/services");
		servicePackage.addResourceFile(new JServiceConfigurationFile(evaluatorUdfClass, Collections.<JClass>singletonList(definedEvaluatorUdfClass)));

		try(OutputStream os = new FileOutputStream(udfJarFile)){
			ArchiverUtil.archive(os, codeModel);
		}
	}
}