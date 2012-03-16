/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.iqchartimport.iq.code;

/**
 * TB screening result code
 */
public enum TBScreenCode {
	
	// TODO missing mappings
	NEGATIVE ("NEGATIVE"),
	POSITIVE ("POSITIVE"),
	NOT_DETECTED (null);
	
	public static final String mappedQuestion = "TB SCREENING";
	public final String mappedAnswer;
	
	/**
	 * Constructs new enum value
	 * @param mappedAnswer the mapped answer
	 */
	TBScreenCode(String mappedAnswer) {
		this.mappedAnswer = mappedAnswer;
	}
	
	/**
	 * Converts a Byte value to an enum value
	 * @param val the Byte value
	 * @return the enum value or null if byte value is null
	 */
	public static TBScreenCode fromByte(Byte val) {
		return val != null ? values()[val] : null;
	}
}
