/*
 * Copyright (c) 2014, salesforce.com, inc.
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided 
 * that the following conditions are met:
 * 
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 *    following disclaimer.
 *  
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and 
 *    the following disclaimer in the documentation and/or other materials provided with the distribution. 
 *    
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or 
 *    promote products derived from this software without specific prior written permission.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED 
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR 
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED 
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.sforce.dataset.loader.file.schema.ext;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class FieldType extends com.sforce.dataset.loader.file.schema.FieldType {
	
	public static final int STRING = 1;
	public static final int MEASURE = 2;
	public static final int DATE = 3;
	public static final int max_precision = 18;
	
	private static final String TEXT_TYPE = "Text";
	private static final String NUMERIC_TYPE = "Numeric";
	private static final String DATE_TYPE = "Date";
	
	private transient int fType = 0;
	private transient long measure_multiplier = 0;
	private transient BigDecimal measure_multiplier_bd = null;

	//private long id; //(generated by workflow when the source is instantiated)
	//private String Alias; //(generated by workflow)

//	private String name = null; //Required
//	private String fullyQualifiedName = null; //Required
//	private String label = null; //Required
//	private String description = null; //Optional
//	private String type = null; //Required - Text, Numeric, Date
//	private int precision = 0; //Required if type is Numeric, the number 256.99 has a precision of 5
//	private int scale = 0; //Required if type is Numeric, the number 256.99 has a scale of 2
//	private String defaultValue = null; //required for numeric types	
//	private String format = null; //Required if type is Numeric or Date
//	public boolean isSystemField = false; //Optional
//	public boolean isUniqueId = false; //Optional
//	public boolean isMultiValue = false; //Optional 
//	private String multiValueSeparator = null; //Optional - only used if IsMultiValue = true separator
//	private int fiscalMonthOffset = 0;
//	private int firstDayOfWeek = -1; //1=SUNDAY, 2=MONDAY etc.. -1 the week starts on 1st day of year and is always 7 days long
//	public boolean canTruncateValue = true; //Optional 
//	public boolean isYearEndFiscalYear = true; //Optional 
//	public boolean isSkipped = false; //Optional 
//	private String decimalSeparator = ".";

	private int sortIndex = 0; //Index start at 1, 0 means not to sort
	public boolean isSortAscending = true; //Optional  if index > 0 then will sort ascending if true
	
	public boolean isComputedField = false; //Optional  if this field is computed
	private String computedFieldExpression = null; //Optional the expression to compute this field	
	private transient CompiledScript compiledScript = null;
	
	private transient SimpleDateFormat compiledDateFormat = null;
	private transient DecimalFormat compiledNumberFormat = null;
	private transient Date defaultDate = null;
	

	public static FieldType GetStringKeyDataType(String name, String multivalueSeperator, String defaultValue)
	{
		FieldType kdt = new FieldType(name);
		kdt.fType = (FieldType.STRING);
		kdt.setType(FieldType.TEXT_TYPE);
		if(multivalueSeperator!=null && multivalueSeperator.length()!=0)
		{
			kdt.multiValueSeparator = multivalueSeperator;
			kdt.isMultiValue = true;
		}
		if(defaultValue!=null && !defaultValue.trim().isEmpty())
			kdt.setDefaultValue(defaultValue);
		kdt.setLabel(name);
		kdt.setFullyQualifiedName(kdt.name);
		kdt.setDescription(name);
		return kdt;
	}
	
	@Override
	public String toString() {
		return "FieldType [name=" + name + ",label=" + label + ", type=" + type
				+ ", defaultValue=" + defaultValue + ", scale=" + scale
				+ ", format=" + format + "]";
	}
	
	public static FieldType GetMeasureKeyDataType(String name,int precision,int scale, Long defaultValue)
	{
		FieldType kdt = new FieldType(name);
		kdt.fType = (FieldType.MEASURE);
		kdt.setType(FieldType.NUMERIC_TYPE);
		if(precision<=0 || precision>max_precision)
			precision = max_precision;
		kdt.setPrecision(precision);
		if(scale<=0)
			scale = 0;
    	//If scale greater than precision, then force the scale to be 1 less than precision
        if (scale > precision)
        	scale = (precision > 1) ? (precision - 1) : 0;
        	
		kdt.setScale(scale);
		kdt.measure_multiplier = (int) Math.pow(10, scale);
		kdt.setDefaultValue(BigDecimal.valueOf(defaultValue).toPlainString());
		kdt.setLabel(name);
		kdt.setFullyQualifiedName(kdt.name);
		kdt.setDescription(name);
		return kdt;
	}

	
	/**
	 * @param name the field name
	 * @param format refer to Date format section in https://developer.salesforce.com/docs/atlas.en-us.bi_dev_guide_ext_data.meta/bi_dev_guide_ext_data/bi_ext_data_schema_reference.htm
	 * @param defaultValue
	 * @return
	 */
	public static FieldType GetDateKeyDataType(String name, String format, String defaultValue)
	{
		FieldType kdt = new FieldType(name);
		kdt.fType = (FieldType.DATE);
		kdt.setType(FieldType.DATE_TYPE);
		new SimpleDateFormat(format);
		kdt.setFormat(format);
		if(defaultValue!=null && !defaultValue.trim().isEmpty())
			kdt.setDefaultValue(defaultValue);
		kdt.setLabel(name);
		kdt.setFullyQualifiedName(kdt.name);
		kdt.setDescription(name);
		return kdt;
	}

	public FieldType() {
		super();
	}
	
	public FieldType(FieldType old) {
		super();
		if(old!=null)
		{
			this.canTruncateValue = old.canTruncateValue;
			this.compiledDateFormat = old.compiledDateFormat;
			this.compiledScript = old.compiledScript;
			this.computedFieldExpression = old.computedFieldExpression;
			this.decimalSeparator = old.decimalSeparator;
			this.defaultDate = old.defaultDate;
			this.defaultValue = old.defaultValue;
			this.description = old.description;
			this.firstDayOfWeek = old.firstDayOfWeek;
			this.fiscalMonthOffset = old.fiscalMonthOffset;
			this.format = old.format;
			this.fType = old.fType;
			this.fullyQualifiedName = old.fullyQualifiedName;
			this.isComputedField = old.isComputedField;
			this.isMultiValue = old.isMultiValue;
			this.isSkipped = old.isSkipped;
			this.isSortAscending = old.isSortAscending;
			this.isSystemField = old.isSystemField;
			this.isUniqueId = old.isUniqueId;
			this.isYearEndFiscalYear = old.isYearEndFiscalYear;
			this.label = old.label;
			this.measure_multiplier = old.measure_multiplier;
			this.multiValueSeparator = old.multiValueSeparator;
			this.name = old.name;
			this.precision = old.precision;
			this.scale = old.scale;
			this.sortIndex = old.sortIndex;
			this.type = old.type;
			
			if(!this.equals(old))
			{
				System.out.println("FieldType Copy constructor is missing functionality");
			}
		}
	}
	

	FieldType(String name) {
		if(name==null||name.isEmpty())
		{
			throw new IllegalArgumentException("field name is null {"+name+"}");			
		}
		if(name.length()>255)
		{
			throw new IllegalArgumentException("field name cannot be greater than 255 characters");			
		}
		this.name = name;
	}

	@JsonIgnore
	public long getMeasure_multiplier() {
		if(type.equals(FieldType.NUMERIC_TYPE) && measure_multiplier==0)
		{
			if(precision<=0)
				precision = max_precision;
			if(scale<=0)
				scale = 0;
	    	//If scale greater than precision, then force the scale to be 1 less than precision
	        if (scale > precision)
	        	scale = (precision > 1) ? (precision - 1) : 0;
				
	    	measure_multiplier = (long) Math.pow(10, scale);
		}
		return measure_multiplier;
	}

	@JsonIgnore
	public BigDecimal getMeasure_multiplier_bd() {
		if(type.equals(FieldType.NUMERIC_TYPE) && measure_multiplier_bd==null)
		{
			measure_multiplier_bd = new BigDecimal(getMeasure_multiplier());
		}
		return measure_multiplier_bd;
	}
	
	@JsonIgnore
	public int getfType() {
		if(fType==0)
		{
			if(this.type != null && this.type.equals(FieldType.DATE_TYPE))
				fType = FieldType.DATE;
			else if(this.type != null && this.type.equals(FieldType.NUMERIC_TYPE))
				fType = FieldType.MEASURE;
			else
				fType = FieldType.STRING;
		}
		return fType;
	}
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		if(type!=null)
			return type;
		else
			return FieldType.TEXT_TYPE;
	}

	public void setType(String type) {
		if(type!=null)
		{
			if(type.equalsIgnoreCase(FieldType.DATE_TYPE))
				this.type = FieldType.DATE_TYPE;
			else if(type.equalsIgnoreCase(FieldType.NUMERIC_TYPE))
				this.type = FieldType.NUMERIC_TYPE;
			else if(type.equalsIgnoreCase(FieldType.TEXT_TYPE))
				this.type = FieldType.TEXT_TYPE;
			else
				throw new IllegalArgumentException("Invalid type: "+type);
		}else
			throw new IllegalArgumentException("Invalid type: "+type);
	}

	public int getPrecision() {
		return precision;
	}

	public void setPrecision(int precision) {
		this.precision = precision;
	}

	public int getScale() {
		return scale;
	}

	public void setScale(int scale) {
		this.scale = scale;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		if(this.type != null && this.type.equals(FieldType.DATE_TYPE) && format != null)
		{
			compiledDateFormat = new SimpleDateFormat(format);
			compiledDateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //All dates must be in GMT
			compiledDateFormat.setLenient(false);
			if(this.defaultValue!=null && !this.defaultValue.isEmpty())
			{
				try {
					this.defaultDate = compiledDateFormat.parse(this.defaultValue);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e.toString());
				}
			}
		}
		this.format = format;
	}

	@JsonIgnore
	public DecimalFormat getCompiledNumberFormat() {
		if(compiledNumberFormat==null)
		{
			if(this.type != null && this.type.equals(FieldType.NUMERIC_TYPE) && format != null && !format.isEmpty())
			{
				DecimalFormat indf = (DecimalFormat) NumberFormat.getCurrencyInstance(Locale.getDefault());
				if(!format.contains(indf.getDecimalFormatSymbols().getCurrencySymbol()))
				{
					indf = (DecimalFormat) NumberFormat.getInstance();
				}
				compiledNumberFormat = indf;
			}
		}
		return compiledNumberFormat;
	}
	
	
	@JsonIgnore
	public SimpleDateFormat getCompiledDateFormat() {
		if(compiledDateFormat==null)
		{
			if(this.type != null && this.type.equals(FieldType.DATE_TYPE) && format != null)
			{
				compiledDateFormat = new SimpleDateFormat(format);
				compiledDateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); //All dates must be in GMT
			}
		}
		return compiledDateFormat;
	}

	@JsonIgnore
	public Date getDefaultDate() {
		if(defaultDate==null)
		{
			if(getCompiledDateFormat() != null && defaultValue!=null && !defaultValue.isEmpty())
			{
				try {
					this.defaultDate = getCompiledDateFormat().parse(defaultValue);
				} catch (ParseException e) {
					throw new IllegalArgumentException(e.toString());
				}
			}
		}
		return defaultDate;
	}

	public String getMultiValueSeparator() {
		return multiValueSeparator;
	}

	public void setMultiValueSeparator(String multiValueSeparator) {
		this.multiValueSeparator = multiValueSeparator;
	}

	public String getDefaultValue() {
		if(defaultValue!=null && !defaultValue.isEmpty())
			return defaultValue;
		return null;
	}

	public void setDefaultValue(String defaultValue) 
	{
		if(defaultValue!=null && !defaultValue.isEmpty())
		{
			if(this.type != null && this.type.equals(FieldType.DATE_TYPE) && getCompiledDateFormat() != null)
			{
					try {
						this.defaultDate = getCompiledDateFormat().parse(defaultValue);
					} catch (ParseException e) {
						throw new IllegalArgumentException(e.toString());
					}
			}
			this.defaultValue = defaultValue;
		}
	}

	public String getFullyQualifiedName() {
		return fullyQualifiedName;
	}

	public void setFullyQualifiedName(String fullyQualifiedName) {
		this.fullyQualifiedName = fullyQualifiedName;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	/*
	@JsonIgnore
	public boolean isNillable() {
		return isNillable;
	}

	@JsonIgnore
	public void setNillable(boolean isNillable) {
		this.isNillable = isNillable;
	}
	*/
	
	@JsonIgnore
	public boolean isSystemField() {
		return isSystemField;
	}

	@JsonIgnore
	public void setSystemField(boolean isSystemField) {
		this.isSystemField = isSystemField;
	}

	@JsonIgnore
	public boolean isUniqueId() {
		return isUniqueId;
	}

	@JsonIgnore
	public void setUniqueId(boolean isUniqueId) {
		this.isUniqueId = isUniqueId;
	}

	@JsonIgnore
	public boolean isMultiValue() {
		return isMultiValue;
	}

	@JsonIgnore
	public void setMultiValue(boolean isMultiValue) {
		this.isMultiValue = isMultiValue;
	}

//	public String getAcl() {
//		return acl;
//	}
//
//	public void setAcl(String acl) {
//		this.acl = acl;
//	}

	/*
	@JsonIgnore
	public boolean isAclField() {
		return isAclField;
	}

	@JsonIgnore
	public void setAclField(boolean isAclField) {
		this.isAclField = isAclField;
	}
	*/
	
	public int getFiscalMonthOffset() {
		return fiscalMonthOffset;
	}
	
	public void setFiscalMonthOffset(int fiscalMonthOffset) {
		this.fiscalMonthOffset = fiscalMonthOffset;
	}

	public String getComputedFieldExpression() {
		return computedFieldExpression;
	}

	public void setComputedFieldExpression(String computedFieldExpression) {
		if(computedFieldExpression != null && computedFieldExpression.length()!=0)
		{
	        try
	        {
			    ScriptEngineManager mgr = new ScriptEngineManager();
		        ScriptEngine jsEngine = mgr.getEngineByName("JavaScript");
		        if (jsEngine instanceof Compilable)
	            {
	                Compilable compEngine = (Compilable)jsEngine;
	                this.compiledScript = compEngine.compile(computedFieldExpression);
	        		this.computedFieldExpression = computedFieldExpression;
	            }
	        } catch(Throwable t)
	        {
	        	throw new IllegalArgumentException(t.toString());
	        }
		}
	}

	@JsonIgnore
	public CompiledScript getCompiledScript() {
		return compiledScript;
	}
	
/*
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
//		result = prime * result + ((acl == null) ? 0 : acl.hashCode());
		result = prime * result
				+ ((defaultValue == null) ? 0 : defaultValue.hashCode());
		result = prime * result
				+ ((description == null) ? 0 : description.hashCode());
		result = prime * result + fiscalMonthOffset;
		result = prime * result + ((format == null) ? 0 : format.hashCode());
		result = prime
				* result
				+ ((fullyQualifiedName == null) ? 0 : fullyQualifiedName
						.hashCode());
		result = prime * result + (isMultiValue ? 1231 : 1237);
		result = prime * result + (isSystemField ? 1231 : 1237);
		result = prime * result + (isUniqueId ? 1231 : 1237);
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		result = prime
				* result
				+ ((multiValueSeparator == null) ? 0 : multiValueSeparator
						.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + precision;
		result = prime * result + scale;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		FieldType other = (FieldType) obj;
//		if (acl == null) {
//			if (other.acl != null) {
//				return false;
//			}
//		} else if (!acl.equals(other.acl)) {
//			return false;
//		}
		if (defaultValue == null) {
			if (other.defaultValue != null) {
				return false;
			}
		} else if (!defaultValue.equals(other.defaultValue)) {
			return false;
		}
		if (description == null) {
			if (other.description != null) {
				return false;
			}
		} else if (!description.equals(other.description)) {
			return false;
		}
		if (fiscalMonthOffset != other.fiscalMonthOffset) {
			return false;
		}
		if (format == null) {
			if (other.format != null) {
				return false;
			}
		} else if (!format.equals(other.format)) {
			return false;
		}
		if (fullyQualifiedName == null) {
			if (other.fullyQualifiedName != null) {
				return false;
			}
		} else if (!fullyQualifiedName.equals(other.fullyQualifiedName)) {
			return false;
		}
		if (isMultiValue != other.isMultiValue) {
			return false;
		}
		if (isSystemField != other.isSystemField) {
			return false;
		}
		if (isUniqueId != other.isUniqueId) {
			return false;
		}
		if (label == null) {
			if (other.label != null) {
				return false;
			}
		} else if (!label.equals(other.label)) {
			return false;
		}
		if (multiValueSeparator == null) {
			if (other.multiValueSeparator != null) {
				return false;
			}
		} else if (!multiValueSeparator.equals(other.multiValueSeparator)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (precision != other.precision) {
			return false;
		}
		if (scale != other.scale) {
			return false;
		}
		if (type == null) {
			if (other.type != null) {
				return false;
			}
		} else if (!type.equals(other.type)) {
			return false;
		}
		return true;
	}
*/
	public int getFirstDayOfWeek() {
		return firstDayOfWeek;
	}

	public void setFirstDayOfWeek(int firstDayOfWeek) {
		this.firstDayOfWeek = firstDayOfWeek;
	}

	@JsonIgnore
	public boolean isCanTruncateValue() {
		return canTruncateValue;
	}

	@JsonIgnore
	public void setCanTruncateValue(boolean canTruncateValue) {
		this.canTruncateValue = canTruncateValue;
	}

	public String getDecimalSeparator() {
		return decimalSeparator;
	}

	public void setDecimalSeparator(String decimalSeparator) {
		if(decimalSeparator == null || decimalSeparator.isEmpty())
			this.decimalSeparator = ".";
		else
			this.decimalSeparator = decimalSeparator;
	}

	public int getSortIndex() {
		return sortIndex;
	}

	public void setSortIndex(int sortIndex) {
		this.sortIndex = sortIndex;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (canTruncateValue ? 1231 : 1237);
		result = prime
				* result
				+ ((computedFieldExpression == null) ? 0
						: computedFieldExpression.hashCode());
		result = prime
				* result
				+ ((decimalSeparator == null) ? 0 : decimalSeparator.hashCode());
		result = prime * result
				+ ((defaultValue == null) ? 0 : defaultValue.hashCode());
		result = prime * result
				+ ((description == null) ? 0 : description.hashCode());
		result = prime * result + firstDayOfWeek;
		result = prime * result + fiscalMonthOffset;
		result = prime * result + ((format == null) ? 0 : format.hashCode());
		result = prime
				* result
				+ ((fullyQualifiedName == null) ? 0 : fullyQualifiedName
						.hashCode());
		result = prime * result + (isComputedField ? 1231 : 1237);
		result = prime * result + (isMultiValue ? 1231 : 1237);
		result = prime * result + (isSkipped ? 1231 : 1237);
		result = prime * result + (isSortAscending ? 1231 : 1237);
		result = prime * result + (isSystemField ? 1231 : 1237);
		result = prime * result + (isUniqueId ? 1231 : 1237);
		result = prime * result + (isYearEndFiscalYear ? 1231 : 1237);
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		result = prime
				* result
				+ ((multiValueSeparator == null) ? 0 : multiValueSeparator
						.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + precision;
		result = prime * result + scale;
		result = prime * result + sortIndex;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		FieldType other = (FieldType) obj;
		if (canTruncateValue != other.canTruncateValue) {
			return false;
		}
		if (computedFieldExpression == null) {
			if (other.computedFieldExpression != null) {
				return false;
			}
		} else if (!computedFieldExpression
				.equals(other.computedFieldExpression)) {
			return false;
		}
		if (decimalSeparator == null) {
			if (other.decimalSeparator != null) {
				return false;
			}
		} else if (!decimalSeparator.equals(other.decimalSeparator)) {
			return false;
		}
		if (defaultValue == null) {
			if (other.defaultValue != null) {
				return false;
			}
		} else if (!defaultValue.equals(other.defaultValue)) {
			return false;
		}
		if (description == null) {
			if (other.description != null) {
				return false;
			}
		} else if (!description.equals(other.description)) {
			return false;
		}
		if (firstDayOfWeek != other.firstDayOfWeek) {
			return false;
		}
		if (fiscalMonthOffset != other.fiscalMonthOffset) {
			return false;
		}
		if (format == null) {
			if (other.format != null) {
				return false;
			}
		} else if (!format.equals(other.format)) {
			return false;
		}
		if (fullyQualifiedName == null) {
			if (other.fullyQualifiedName != null) {
				return false;
			}
		} else if (!fullyQualifiedName.equals(other.fullyQualifiedName)) {
			return false;
		}
		if (isComputedField != other.isComputedField) {
			return false;
		}
		if (isMultiValue != other.isMultiValue) {
			return false;
		}
		if (isSkipped != other.isSkipped) {
			return false;
		}
		if (isSortAscending != other.isSortAscending) {
			return false;
		}
		if (isSystemField != other.isSystemField) {
			return false;
		}
		if (isUniqueId != other.isUniqueId) {
			return false;
		}
		if (isYearEndFiscalYear != other.isYearEndFiscalYear) {
			return false;
		}
		if (label == null) {
			if (other.label != null) {
				return false;
			}
		} else if (!label.equals(other.label)) {
			return false;
		}
		if (multiValueSeparator == null) {
			if (other.multiValueSeparator != null) {
				return false;
			}
		} else if (!multiValueSeparator.equals(other.multiValueSeparator)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (precision != other.precision) {
			return false;
		}
		if (scale != other.scale) {
			return false;
		}
		if (sortIndex != other.sortIndex) {
			return false;
		}
		if (type == null) {
			if (other.type != null) {
				return false;
			}
		} else if (!type.equals(other.type)) {
			return false;
		}
		return true;
	}


	
}