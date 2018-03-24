JPMML-Evaluator-Hive
====================

PMML evaluator library for the Apache Hive data warehouse software (http://hive.apache.org/).

# Features #

* Full support for PMML specification versions 3.0 through 4.3. The evaluation is handled by the [JPMML-Evaluator](https://github.com/jpmml/jpmml-evaluator) library.

# Prerequisites #

* Apache Hadoop 2.7.0 or newer.
* Apache Hive version 1.2.0 or newer.

# Installation #

Enter the project root directory and build using [Apache Maven](http://maven.apache.org/):
```
mvn clean install
```

The build produces two JAR files:

* `target/jpmml-evaluator-hive-1.0-SNAPSHOT.jar` - the library JAR file.
* `target/jpmml-evaluator-hive-runtime-1.0-SNAPSHOT.jar` - the runtime uber-JAR file (the library JAR file plus all its transitive dependencies).

# Usage #

Add the runtime uber-JAR file to Apache Hive classpath:
```
ADD JAR jpmml-evaluator-hive-runtime-1.0-SNAPSHOT.jar;
```

Create a subclass of `org.jpmml.evaluator.hive.EvaluatorUDF`:
```Java
package com.mycompany;

import org.jpmml.evaluator.hive.ArchiveResource;
import org.jpmml.evaluator.hive.EvaluatorUDF;

public class DecisionTreeIris extends EvaluatorUDF {

	public DecisionTreeiris(){
		super(new ArchiveResource("/DecisionTreeIris.pmml"){ /* Anonymous inner class */ });
	}

	@Override
	public String getFuncName(){
		return "DecisionTreeIris";
	}
}
```

Package this class together with the accompanying PMML document into a model JAR file.

Add the model JAR file to Apache Hive classpath:
```
ADD JAR DecisionTreeIris.jar;
```

Define a function:
```
CREATE TEMPORARY FUNCTION DecisionTreeIris AS 'com.mycompany.DecisionTreeIris';

DESCRIBE FUNCTION EXTENDED DecisionTreeIris;
```

Load and score the Iris dataset:
```
CREATE EXTERNAL TABLE IF NOT EXISTS Iris (Sepal_Length DOUBLE, Sepal_Width DOUBLE, Petal_Length DOUBLE, Petal_Width DOUBLE)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY ','
STORED AS TEXTFILE
LOCATION '/path/to/Iris';

SELECT DecisionTreeIris(named_struct('Sepal_Length', Sepal_Length, 'Sepal_Width', Sepal_Width, 'Petal_Length', Petal_Length, 'Petal_Width', Petal_Width)) FROM Iris;
```

# License #

JPMML-Evaluator-Hive is licensed under the [GNU Affero General Public License (AGPL) version 3.0](http://www.gnu.org/licenses/agpl-3.0.html). Other licenses are available on request.

# Additional information #

Please contact [info@openscoring.io](mailto:info@openscoring.io)
