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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;
import javax.xml.transform.Source;

import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.hadoop.hive.serde2.objectinspector.StructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.InputField;
import org.jpmml.evaluator.ModelEvaluatorFactory;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.TargetField;
import org.jpmml.model.ImportFilter;
import org.jpmml.model.JAXBUtil;
import org.jpmml.model.visitors.LocatorTransformer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

abstract
public class EvaluatorUDF extends GenericUDF {

	private Resource resource = null;

	private Evaluator evaluator = null;

	private StructObjectInspector inputInspector = null;

	private List<Mapping<InputField>> inputMappings = null;

	private StructObjectInspector outputInspector = null;

	private List<Mapping<ResultField>> outputMappings = null;


	public EvaluatorUDF(Resource resource){
		setResource(resource);
	}

	@Override
	public String getFuncName(){
		return "PMML";
	}

	@Override
	public String getDisplayString(String[] children){
		return getStandardDisplayString(getFuncName(), children);
	}

	@Override
	public ObjectInspector initialize(ObjectInspector[] inputInspectors) throws UDFArgumentException {
		Evaluator evaluator;

		try {
			evaluator = ensureEvaluator();
		} catch(Exception e){
			throw new UDFArgumentException(e);
		}

		checkArgsSize(inputInspectors, 1, 1);

		StructObjectInspector inputStructInspector = asStructOfPrimitivesInspector(inputInspectors[0]);
		if(inputStructInspector == null){
			throw new UDFArgumentTypeException(0, getFuncName() + " only takes the struct of primitive types as the " + getArgOrder(0) + " argument");
		}

		List<Mapping<InputField>> inputMappings = new ArrayList<>();

		List<InputField> inputFields = evaluator.getInputFields();
		for(InputField inputField : inputFields){
			FieldName name = inputField.getName();

			StructField structField = inputStructInspector.getStructFieldRef(name.getValue());
			if(structField == null){
				throw new UDFArgumentException("Input field " + name.getValue() + " does not have a struct field mapping");
			}

			inputMappings.add(new Mapping<>(inputField, structField));
		}

		setInputInspector(inputStructInspector);
		setInputMappings(inputMappings);

		List<ResultField> resultFields = new ArrayList<>();
		resultFields.addAll(evaluator.getTargetFields());
		resultFields.addAll(evaluator.getOutputFields());

		List<String> names = new ArrayList<>();
		List<ObjectInspector> structFieldInspectors = new ArrayList<>();

		List<Mapping<ResultField>> outputMappings = new ArrayList<>();

		for(ResultField resultField : resultFields){
			FieldName name = resultField.getName();
			DataType dataType = resultField.getDataType();

			PrimitiveObjectInspector structFieldInspector = getObjectInspector(dataType);
			if(structFieldInspector == null){
				throw new UDFArgumentException();
			}

			names.add(name.getValue());
			structFieldInspectors.add(structFieldInspector);
		}

		StructObjectInspector outputStructInspector = ObjectInspectorFactory.getStandardStructObjectInspector(names, structFieldInspectors);

		for(ResultField resultField : resultFields){
			FieldName name = resultField.getName();

			StructField structField = outputStructInspector.getStructFieldRef(name.getValue());

			outputMappings.add(new Mapping<>(resultField, structField));
		}

		setOutputInspector(outputStructInspector);
		setOutputMappings(outputMappings);

		return outputStructInspector;
	}

	@Override
	public Object evaluate(DeferredObject[] inputs) throws HiveException {
		Evaluator evaluator;

		try {
			evaluator = ensureEvaluator();
		} catch(Exception e){
			throw new HiveException(e);
		}

		Object[] input = (Object[])inputs[0].get();

		Map<FieldName, FieldValue> arguments = decodeInput(input);

		Map<FieldName, ?> result = evaluator.evaluate(arguments);

		return encodeOutput(result);
	}

	private StructObjectInspector asStructOfPrimitivesInspector(ObjectInspector objectInspector){

		if(!(ObjectInspector.Category.STRUCT).equals(objectInspector.getCategory())){
			return null;
		}

		StructObjectInspector structInspector = (StructObjectInspector)objectInspector;

		List<? extends StructField> structFields = structInspector.getAllStructFieldRefs();
		for(StructField structField : structFields){
			ObjectInspector structFieldInspector = structField.getFieldObjectInspector();

			if(!(ObjectInspector.Category.PRIMITIVE).equals(structFieldInspector.getCategory())){
				return null;
			}
		}

		return structInspector;
	}

	private Map<FieldName, FieldValue> decodeInput(Object[] input){
		StructObjectInspector inputInspector = getInputInspector();
		List<Mapping<InputField>> inputMappings = getInputMappings();

		Map<FieldName, FieldValue> result = new HashMap<>();

		for(Mapping<InputField> inputMapping : inputMappings){
			InputField inputField = inputMapping.getField();
			StructField structField = inputMapping.getStructField();

			Object hiveValue = inputInspector.getStructFieldData(input, structField);

			PrimitiveObjectInspector inputStructFieldInspector = (PrimitiveObjectInspector)structField.getFieldObjectInspector();

			hiveValue = inputStructFieldInspector.getPrimitiveJavaObject(hiveValue);

			// XXX
			if(hiveValue instanceof HiveDecimal){
				HiveDecimal hiveDecimal = (HiveDecimal)hiveValue;

				hiveValue = hiveDecimal.doubleValue();
			}

			FieldName name = inputField.getName();
			FieldValue value = inputField.prepare(hiveValue);

			result.put(name, value);
		}

		return result;
	}

	private Object[] encodeOutput(Map<FieldName, ?> result){
		List<Mapping<ResultField>> outputMappings = getOutputMappings();

		Object[] output = new Object[outputMappings.size()];

		int position = 0;

		for(Mapping<ResultField> outputMapping : outputMappings){
			ResultField resultField = outputMapping.getField();

			Object pmmlValue = result.get(resultField.getName());

			if(resultField instanceof TargetField){
				pmmlValue = EvaluatorUtil.decode(pmmlValue);
			}

			output[position] = pmmlValue;

			position++;
		}

		return output;
	}

	private Evaluator ensureEvaluator() throws Exception {

		if(this.evaluator == null){
			this.evaluator = createEvaluator();
		}

		return this.evaluator;
	}

	private Evaluator createEvaluator() throws Exception {
		Resource resource = getResource();

		try(InputStream is = resource.getInputStream()){
			return createEvaluator(is);
		}
	}

	public Resource getResource(){
		return this.resource;
	}

	private void setResource(Resource resource){
		this.resource = resource;
	}

	public StructObjectInspector getInputInspector(){
		return this.inputInspector;
	}

	private void setInputInspector(StructObjectInspector inputInspector){
		this.inputInspector = inputInspector;
	}

	public List<Mapping<InputField>> getInputMappings(){
		return this.inputMappings;
	}

	private void setInputMappings(List<Mapping<InputField>> inputMappings){
		this.inputMappings = inputMappings;
	}

	public StructObjectInspector getOutputInspector(){
		return this.outputInspector;
	}

	private void setOutputInspector(StructObjectInspector outputInspector){
		this.outputInspector = outputInspector;
	}

	public List<Mapping<ResultField>> getOutputMappings(){
		return this.outputMappings;
	}

	private void setOutputMappings(List<Mapping<ResultField>> outputMappings){
		this.outputMappings = outputMappings;
	}

	static
	private PrimitiveObjectInspector getObjectInspector(DataType dataType){

		switch(dataType){
			case STRING:
				return PrimitiveObjectInspectorFactory.javaStringObjectInspector;
			case INTEGER:
				return PrimitiveObjectInspectorFactory.javaIntObjectInspector;
			case FLOAT:
				return PrimitiveObjectInspectorFactory.javaFloatObjectInspector;
			case DOUBLE:
				return PrimitiveObjectInspectorFactory.javaDoubleObjectInspector;
			case BOOLEAN:
				return PrimitiveObjectInspectorFactory.javaBooleanObjectInspector;
			default:
				return null;
		}
	}

	static
	private Evaluator createEvaluator(InputStream is) throws SAXException, JAXBException {
		Source source = ImportFilter.apply(new InputSource(is));

		PMML pmml = JAXBUtil.unmarshalPMML(source);

		// If the SAX Locator information is available, then transform it to java.io.Serializable representation
		LocatorTransformer locatorTransformer = new LocatorTransformer();
		locatorTransformer.applyTo(pmml);

		ModelEvaluatorFactory modelEvaluatorFactory = ModelEvaluatorFactory.newInstance();

		Evaluator evaluator = modelEvaluatorFactory.newModelEvaluator(pmml);

		// Perform self-testing
		evaluator.verify();

		return evaluator;
	}
}