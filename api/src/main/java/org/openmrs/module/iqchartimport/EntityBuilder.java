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

package org.openmrs.module.iqchartimport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PatientProgram;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Program;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.iqchartimport.iq.IQChartSession;
import org.openmrs.module.iqchartimport.iq.IQPatient;
import org.openmrs.module.iqchartimport.iq.code.ExitCode;
import org.openmrs.module.iqchartimport.iq.code.SexCode;
import org.openmrs.module.iqchartimport.iq.code.StatusCode;

/**
 * Builder which creates OpenMRS entities from IQChart objects equivalents
 */
public class EntityBuilder {

	protected static final Log log = LogFactory.getLog(EntityBuilder.class);
	
	private IQChartSession session;
	
	/**
	 * Creates a new entity builder
	 * @param session the IQChart session to load data from
	 */
	public EntityBuilder(IQChartSession session) {
		this.session = session;
	}
	
	/**
	 * Gets the TRACnetID identifier type to use for imported patients. Depending on module
	 * options this will load existing identifier type or create a new one.
	 * @return the identifier type
	 * @throws IncompleteMappingException if mappings are not configured properly
	 */
	public PatientIdentifierType getTRACnetIDType() {
		int tracnetIDTypeId = Mappings.getInstance().getTracnetIDTypeId();
		if (tracnetIDTypeId > 0) {
			return Context.getPatientService().getPatientIdentifierType(tracnetIDTypeId);	
		}
		else if (tracnetIDTypeId == 0) {
			PatientIdentifierType tracnetIDType = new PatientIdentifierType();
			tracnetIDType.setName(Constants.NEW_TRACNET_ID_TYPE_NAME);
			tracnetIDType.setDescription(Constants.NEW_TRACNET_ID_TYPE_NAME);
			tracnetIDType.setFormat(Constants.NEW_TRACNET_ID_TYPE_FORMAT);
			tracnetIDType.setFormatDescription(Constants.NEW_TRACNET_ID_TYPE_FORMAT_DESC);
			return tracnetIDType;
		}
		else {
			throw new IncompleteMappingException();
		}
	}
	
	/**
	 * Gets the HIV program to use for imported patients
	 * @return the HIV program
	 * @throws IncompleteMappingException if mappings are not configured properly
	 */
	public Program getHIVProgram() {
		int hivProgramId = Mappings.getInstance().getHivProgramId();
		if (hivProgramId > 0) {
			return Context.getProgramWorkflowService().getProgram(hivProgramId);	
		}
		else {
			throw new IncompleteMappingException();
		}
	}
	
	/**
	 * Gets the visit encounter type to use for imported obs
	 * @return the encounter type
	 * @throws IncompleteMappingException if mappings are not configured properly
	 */
	public EncounterType getEncounterType() {
		int visitEncounterTypeId = Mappings.getInstance().getEncounterTypeId();
		if (visitEncounterTypeId > 0) {
			return Context.getEncounterService().getEncounterType(visitEncounterTypeId);	
		}
		else if (visitEncounterTypeId == 0) {
			EncounterType encounterType = new EncounterType();
			encounterType.setName(Constants.NEW_VISIT_ENCOUNTER_NAME);
			encounterType.setDescription(Constants.NEW_VISIT_ENCOUNTER_DESCRIPTION);
			return encounterType;
		}
		else {
			throw new IncompleteMappingException();
		}
	}
	
	/**
	 * Gets the location for imported encounters
	 * @return the location
	 * @throws IncompleteMappingException if mappings are not configured properly
	 */
	public Location getEncounterLocation() {
		int encounterLocationId = Mappings.getInstance().getEncounterLocationId();
		if (encounterLocationId > 0) {
			return Context.getLocationService().getLocation(encounterLocationId);	
		}
		else {
			throw new IncompleteMappingException();
		}
	}
	
	/**
	 * Gets a patient by TRACnet ID
	 * @param tracnetID the TRACnet ID
	 * @return the patient
	 */
	public Patient getPatient(int tracnetID) {
		IQPatient iqPatient = session.getPatient(tracnetID);
		return iqPatient != null ? convertPatient(iqPatient) : null;
	}
	
	/**
	 * Gets OpenMRS patient objects for each patient in IQChart 
	 * @return the patients
	 * @throws APIException if module is not configured properly
	 */
	public List<Patient> getPatients() {
		List<Patient> patients = new ArrayList<Patient>();
		
		for (IQPatient iqPatient : session.getPatients()) {
			patients.add(convertPatient(iqPatient));
		}
		
		return patients;
	}
	
	/**
	 * Gets the patient's programs
	 * @param tracnetID the patient TRACnet ID
	 * @return the patient programs
	 */
	public List<PatientProgram> getPatientPrograms(int tracnetID) {		
		List<PatientProgram> patientPrograms = new ArrayList<PatientProgram>();
		IQPatient iqPatient = session.getPatient(tracnetID);
		
		if (iqPatient.getEnrollDate() != null) {
			// Get HIV program to use
			Program hivProgram = getHIVProgram();
			
			PatientProgram hivPatientProgram = new PatientProgram();
			hivPatientProgram.setProgram(hivProgram);
			hivPatientProgram.setDateEnrolled(iqPatient.getEnrollDate());
			hivPatientProgram.setDateCompleted(iqPatient.getExitDate());
			patientPrograms.add(hivPatientProgram);
		}
		
		return patientPrograms;
	}
	
	public List<Encounter> getPatientEncounters(Patient patient, int tracnetID) {
		Set<Encounter> encounters = new HashSet<Encounter>();
		IQPatient iqPatient = session.getPatient(tracnetID);
		
		// Create initial encounter
		
		// Create obs encounters
		
		// Create exit encounter
		if (iqPatient.getExitDate() != null) {
			Encounter encounter = getEncounterForDate(encounters, patient, iqPatient.getExitDate());
			encounter.addObs(getPatientExitObs(patient, tracnetID));
			encounters.add(encounter);
		}
		
		// Copy encounters into a list and sort by date
		List<Encounter> encountersSorted = new ArrayList<Encounter>(encounters);
		Collections.sort(encountersSorted, new Comparator<Encounter>() {
			@Override
			public int compare(Encounter enc1, Encounter enc2) {
				return enc1.getEncounterDatetime().compareTo(enc2.getEncounterDatetime());
			}
		});
		return encountersSorted;
	}
	
	/**
	 * Gets the exit reason obs for the given patient
	 * @param patient the patient
	 * @param tracnetID the TRACnet ID
	 * @return
	 */
	public Obs getPatientExitObs(Patient patient, int tracnetID) {
		IQPatient iqPatient = session.getPatient(tracnetID);
		
		if (iqPatient.getStatusCode() != null && iqPatient.getStatusCode() == StatusCode.EXITED && iqPatient.getExitCode() != null) {
			// Make exit reason obs
			String codProp = Context.getAdministrationService().getGlobalProperty("concept.reasonExitedCare");
			Concept reasonConcept = Context.getConceptService().getConcept(codProp);
			
			if (reasonConcept != null) {
				Concept causeConcept = null;	
				if (iqPatient.getExitCode() == ExitCode.DECEASED) {
					codProp = Context.getAdministrationService().getGlobalProperty("concept.patientDied");
					causeConcept = Context.getConceptService().getConcept(codProp);
				}
				else if (iqPatient.getExitCode() == ExitCode.TRANSFERRED) {
					// TODO load concepts from mappings?
					causeConcept = Context.getConceptService().getConcept("PATIENT TRANSFERRED OUT");
				}
				else if (iqPatient.getExitCode() == ExitCode.LOST) {
					causeConcept = Context.getConceptService().getConcept("PATIENT DEFAULTED");
				}
				
				if (causeConcept != null) {
					Obs obsExit = new Obs();
					obsExit.setPerson(patient);
					obsExit.setConcept(reasonConcept);
					obsExit.setLocation(Context.getLocationService().getDefaultLocation());
					obsExit.setObsDatetime(iqPatient.getExitDate());
					obsExit.setValueCoded(causeConcept);
					return obsExit;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Converts an IQChart patient to an OpenMRS patient
	 * @param iqPatient the IQChart patient
	 * @return the OpenMRS patient
	 * @throws IncompleteMappingException if mapping is not configured properly
	 */
	private Patient convertPatient(IQPatient iqPatient) {
		// Get TRACnet ID identifier type
		PatientIdentifierType tracnetIDType = getTRACnetIDType();
		
		Patient patient = new Patient();
		
		// Create TRACnet identifier
		PatientIdentifier tracnetID = new PatientIdentifier();
		tracnetID.setIdentifier("" + iqPatient.getTracnetID());
		tracnetID.setIdentifierType(tracnetIDType);
		tracnetID.setPreferred(true);
		patient.addIdentifier(tracnetID);
		
		// Create name object
		PersonName name = new PersonName();
		name.setGivenName(iqPatient.getFirstName());
		name.setFamilyName(iqPatient.getLastName());
		patient.addName(name);
		
		// Create address object using AddressHierarchRwanda mappings
		String province = Mappings.getInstance().getAddressProvince();
		String country = Context.getAdministrationService().getGlobalProperty(Constants.PROP_ADDRESS_COUNTRY);
		PersonAddress address = new PersonAddress();
		address.setNeighborhoodCell(iqPatient.getCellule());
		address.setCityVillage(iqPatient.getSector());
		address.setCountyDistrict(iqPatient.getDistrict());
		address.setStateProvince(province);
		address.setCountry(country);
		patient.addAddress(address);
		
		// Set patient gender
		if (iqPatient.getSexCode() != null) {
			if (iqPatient.getSexCode() == SexCode.MALE)
				patient.setGender("M");
			else if (iqPatient.getSexCode() == SexCode.FEMALE)
				patient.setGender("F");
		}
		
		// Set patient birth date
		patient.setBirthdate(iqPatient.getDob());
		patient.setBirthdateEstimated(iqPatient.isDobEstimated());
		
		// Set living/dead
		if (iqPatient.getExitCode() != null && iqPatient.getExitCode() == ExitCode.DECEASED)
			patient.setDead(true);
		
		return patient;
	}
	
	/**
	 * Gets an encounter for the given day - if one exists it is returned
	 * @param encounters the existing encounters
	 * @param date the day
	 * @return the encounter
	 */
	private Encounter getEncounterForDate(Set<Encounter> encounters, Patient patient, Date date) {
		// Search for existing one
		for (Encounter enc : encounters) {
			if (enc.getEncounterDatetime().equals(date))
				return enc;
		}
		
		// Create new one
		Encounter encounter = new Encounter();
		encounter.setEncounterType(getEncounterType());
		encounter.setLocation(getEncounterLocation());
		encounter.setEncounterDatetime(date);
		encounter.setPatient(patient);
		return encounter;
	}
}