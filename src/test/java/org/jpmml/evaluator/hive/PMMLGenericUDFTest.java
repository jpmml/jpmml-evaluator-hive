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

import java.util.Collections;
import java.util.List;

import com.klarna.hiverunner.HiveShell;
import com.klarna.hiverunner.StandaloneHiveRunner;
import com.klarna.hiverunner.annotations.HiveSQL;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(StandaloneHiveRunner.class)
public class PMMLGenericUDFTest {

	@HiveSQL (
		files = {}
	)
	private HiveShell shell = null;


	@Test
	public void evaluate(){
		this.shell.execute("CREATE TEMPORARY TABLE Iris (Sepal_Length DOUBLE, Sepal_Width DOUBLE, Petal_Length DOUBLE, Petal_Width DOUBLE)");

		this.shell.execute("INSERT INTO Iris VALUES (5.1, 3.5, 1.4, 0.2)");
		this.shell.execute("INSERT INTO Iris VALUES (7, 3.2, 4.7, 1.4)");
		this.shell.execute("INSERT INTO Iris VALUES (6.3, 3.3, 6, 2.5)");

		this.shell.execute("CREATE TEMPORARY FUNCTION DecisionTreeIris AS '" + DecisionTreeIris.class.getName() + "'");

		List<String> results = this.shell.executeQuery("SELECT DecisionTreeIris(named_struct('Sepal_Length', Sepal_Length, 'Sepal_Width', Sepal_Width, 'Petal_Length', Petal_Length, 'Petal_Width', Petal_Width)) FROM Iris");

		String[][] expectedResults = {
			{"setosa", "2"},
			{"versicolor", "6"},
			{"virginica", "7"},
		};

		assertEquals(expectedResults.length, results.size());

		// XXX
		Collections.reverse(results);

		for(int i = 0; i < expectedResults.length; i++){
			assertEquals("{\"species\":\"" + expectedResults[i][0] + "\",\"node_id\":\"" + expectedResults[i][1] + "\"}", results.get(i));
		}
	}
}