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

package org.openmrs.module.iqchartimport.iq.obs;

import java.util.Date;

public class CD4Obs extends BaseIQObs {

	private Short cd4Count;
	private String testType;
	
	public CD4Obs(Date date, Short cd4Count) {
		super(date);
		this.cd4Count = cd4Count;
	}
	
	public Short getCd4Count() {
		return cd4Count;
	}
	
	public String getTestType() {
		return testType;
	}
	
	public void setTestType(String testType) {
		this.testType = testType;
	}
}
