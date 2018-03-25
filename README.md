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

### Configuring the runtime ###

Add the runtime uber-JAR file to Apache Hive classpath:
```
ADD JAR jpmml-evaluator-hive-runtime-1.0-SNAPSHOT.jar;
```

The PMML model evaluation functionality is implemented by the `org.jpmml.evaluator.hive.EvaluatorUDF` UDF class. However, this is an abstract class, which needs to be subclassed and parameterized with model evaluator information before it can be used in Apache Hive queries.

### Building PMML functions manually ###

Create a subclass of the `EvaluatorUDF` UDF class:
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

Package this class together with the accompanying PMML resource (and other supporting information such as the Service Loader configuration file) into a model JAR file:
```
$ unzip -l DecisionTreeIris.jar 
Archive:  DecisionTreeIris.jar
  Length      Date    Time    Name
---------  ---------- -----   ----
       25  03-25-2018 00:57   META-INF/MANIFEST.MF
     4306  03-25-2018 00:57   DecisionTreeIris.pmml
       30  03-25-2018 00:57   META-INF/services/org.jpmml.evaluator.hive.EvaluatorUDF
      396  03-25-2018 00:57   com/mycompany/DecisionTreeIris.java
      371  03-25-2018 00:57   com/mycompany/DecisionTreeIris$1.class
      526  03-25-2018 00:57   com/mycompany/DecisionTreeIris.class
---------                     -------
     5654                     6 files
```

### Building PMML functions using the Archive Builder function ###

The model JAR building functionality is implemented by the `org.jpmml.evaluator.hive.ArchiveBuilderUDF` UDF class.

Define and inspect the Archive Builder function:
```
CREATE TEMPORARY FUNCTION BuildArchive AS 'org.jpmml.evaluator.hive.ArchiveBuilderUDF';

DESCRIBE FUNCTION BuildArchive;
DESCRIBE FUNCTION EXTENDED BuildArchive;
```

The Archive Builder function takes three strings values as input (the fully qualified name of the PMML UDF subclass, the paths to the PMML file and the model JAR file in local filesystem), and produces a string value (the absolute path to the model JAR file in local filesystem) as output:
```
SELECT BuildArchive('com.mycompany.DecisionTreeIris', '/path/to/DecisionTreeIris.pmml', '/path/to/DecisionTreeIris.jar');
```

### Defining PMML functions ###

Add the model JAR file to Apache Hive classpath:
```
ADD JAR /path/to/DecisionTreeIris.jar;
```

Define and inspect the PMML function:
```
CREATE TEMPORARY FUNCTION DecisionTreeIris AS 'com.mycompany.DecisionTreeIris';

DESCRIBE FUNCTION DecisionTreeIris;
DESCRIBE FUNCTION EXTENDED DecisionTreeIris;
```

### Applying PMML functions ###

All PMML functions take a named struct of primitive values as input, and produce another named struct as output.

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
