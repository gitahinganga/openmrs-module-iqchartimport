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

package org.openmrs.module.iqchartimport.task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PatientProgram;
import org.openmrs.api.context.Context;
import org.openmrs.module.iqchartimport.DrugMapping;
import org.openmrs.module.iqchartimport.EntityBuilder;
import org.openmrs.module.iqchartimport.IncompleteMappingException;
import org.openmrs.module.iqchartimport.Mappings;
import org.openmrs.module.iqchartimport.iq.IQChartDatabase;
import org.openmrs.module.iqchartimport.iq.IQChartSession;
import org.openmrs.util.OpenmrsUtil;

/**
 * Database import task
 */
public class ImportTask implements Runnable {

	protected static final Log log = LogFactory.getLog(ImportTask.class);
			
	private IQChartDatabase database;
	private EntityBuilder builder;
	private Date startTime = null;
	private Date endTime = null;
	private int totalPatients = 0;
	private int processedPatients = 0;
	private int importedPatients = 0;
	private int importedEncounters = 0;
	private int importedObservations = 0;
	private int importedOrders = 0;
	private Exception exception;
	private List<ImportIssue> issues = new ArrayList<ImportIssue>();
	
	/**
	 * Creates a new import task
	 * @param database the database to import from
	 */
	protected ImportTask(IQChartDatabase database) {
		this.database = database;
	}
	
	@Override
	public void run() {
		IQChartSession session = null;
		startTime = new Date();
		
		try {
			log.info("Starting IQChart database import");
			
			session = new IQChartSession(database);
			
			Context.openSession();
			
			// Authenticate as the scheduler user
			Properties props = Context.getRuntimeProperties();
			Context.authenticate(props.getProperty("scheduler.username"), props.getProperty("scheduler.password"));
			
			doImport(session);
			
			log.info("Finished IQChart database import");
		}
		catch (Exception ex) {
			exception = ex;
			
			log.error("Unable to complete IQChart database import", ex);
		}
		finally {
			Context.closeSession();
			try {
				if (session != null)
					session.close();
			} catch (IOException e) {
				log.error("Unable to close OpenMRS session");
			}
			endTime = new Date();
		}
	}
	
	/**
	 * Does the actual import work
	 * @param session the session
	 */
	private void doImport(IQChartSession session) {
		builder = new EntityBuilder(session);
		
		// Load mappings
		Mappings.getInstance().load();
		DrugMapping.load();
		
		List<Patient> patients = builder.getPatients();
		totalPatients = patients.size();
		
		// Get the TracNet ID type
		PatientIdentifierType tracnetIdType = builder.getTRACnetIDType();
		
		// Import each patient
		for (Patient patient : patients) {
			++processedPatients;
			
			// Get TRACnet ID
			PatientIdentifier tracnetIdentifier = patient.getPatientIdentifier();
			int tracnetID = Integer.parseInt(tracnetIdentifier.getIdentifier());
			
			// Look for existing patients
			List<Patient> existingPatients = Context.getPatientService().getPatients(
					null, tracnetIdentifier.getIdentifier(), Collections.singletonList(tracnetIdType), true
			);
			
			Collection<PatientProgram> existingPatientPrograms = null;
			List<Encounter> existingEncounters = null;
			List<DrugOrder> existingDrugOrders = null;
			
			if (existingPatients.size() > 0) {
				log.info("Found existing patient with identifier '" + tracnetID + "'");
				
				patient = existingPatients.get(0);
				existingPatientPrograms = Context.getProgramWorkflowService().getPatientPrograms(patient, null, null, null, null, null, true);
				existingEncounters = Context.getEncounterService().getEncountersByPatient(patient);
				existingDrugOrders = Context.getOrderService().getDrugOrdersByPatient(patient);
			}
			else {
				// Save patient to database
				Context.getPatientService().savePatient(patient);
			}
			
			savePatientPrograms(patient, tracnetID, existingPatientPrograms);
			savePatientEncounters(patient, tracnetID, existingEncounters);
			savePatientDrugOrders(patient, tracnetID, existingDrugOrders);
		
			// Clear out Hibernate session to prevent things getting clogged up
			Context.flushSession();
			Context.clearSession();
			
			// Break cleanly if thread has been interrupted
			if (Thread.currentThread().isInterrupted())
				break;
			
			++importedPatients;
		}
	}
	
	/**
	 * @param patient
	 * @param tracnetID
	 * @param existingPatientPrograms 
	 */
	private void savePatientPrograms(Patient patient, int tracnetID, Collection<PatientProgram> existingPatientPrograms) {
		// Save patient programs
		for (PatientProgram patientProgram : builder.getPatientPrograms(patient, tracnetID)) {
			
			// Check for existing duplicates
			if (existingPatientPrograms != null) {
				for (PatientProgram existingPatientProgram : existingPatientPrograms) {
					if (existingPatientProgram.getProgram().equals(patientProgram.getProgram())
							&& OpenmrsUtil.nullSafeEquals(existingPatientProgram.getDateEnrolled(), patientProgram.getDateEnrolled())
							&& OpenmrsUtil.nullSafeEquals(existingPatientProgram.getDateCompleted(), patientProgram.getDateCompleted()))
						continue;
				}
			}
			
			Context.getProgramWorkflowService().savePatientProgram(patientProgram);
		}
	}

	/**
	 * @param patient
	 * @param tracnetID
	 * @param existingEncounters 
	 */
	private void savePatientEncounters(Patient patient, int tracnetID, List<Encounter> existingEncounters) {
		for (Encounter encounter : builder.getPatientEncounters(patient, tracnetID)) {
			
			// Check for existing duplicates
			if (existingEncounters != null) {
				for (Encounter existingEncounter : existingEncounters) {
					if (existingEncounter.getEncounterDatetime().equals(encounter.getEncounterDatetime()))
						continue;
				}
			}
			
			// Check for null value obss
			Iterator<Obs> iter = encounter.getObs().iterator();
			while (iter.hasNext()) {
				Obs obs = iter.next();
				
				String dataType = obs.getConcept().getDatatype().getHl7Abbreviation();
				boolean isNumeric = dataType.equals("NM") || dataType.equals("SN");
				boolean isCoded = dataType.equals("CWE");
				
				if ((isNumeric && obs.getValueNumeric() == null) || (isCoded && obs.getValueCoded() == null)) {
					// Refetch the concept as the cached concepts' session is closed
					Concept concept = Context.getConceptService().getConcept(obs.getConcept().getConceptId());
					
					issues.add(new ImportIssue(patient, "Null obs value for " + concept.getName().getName() + " on " + Context.getDateFormat().format(encounter.getEncounterDatetime()) +  ". Removing obs"));
					iter.remove();
				}
			}
			
			Context.getEncounterService().saveEncounter(encounter);		
			++importedEncounters;
			importedObservations += encounter.getObs().size();
		}
	}
	
	/**
	 * @param patient
	 * @param tracnetID
	 * @param existingDrugOrders 
	 */
	private void savePatientDrugOrders(Patient patient, int tracnetID, List<DrugOrder> existingDrugOrders) {
		try {
			for (DrugOrder order : builder.getPatientDrugOrders(patient, tracnetID)) {
				
				// Check for existing duplicates
				if (existingDrugOrders != null) {
					for (DrugOrder existingDrugOrder : existingDrugOrders) {
						if (existingDrugOrder.getConcept().equals(order.getConcept())
								&& OpenmrsUtil.nullSafeEquals(existingDrugOrder.getStartDate(), order.getStartDate())
								&& OpenmrsUtil.nullSafeEquals(existingDrugOrder.getDiscontinuedDate(), order.getDiscontinuedDate()))
							continue;
					}
				}
				
				// Check incorrect discontinued dates
				Date discontinuedDate = order.getDiscontinuedDate();
				if (discontinuedDate != null && order.getStartDate().after(discontinuedDate)) {
					issues.add(new ImportIssue(patient, "Drug order discontinued date before start date. Removing discontinued date."));
					order.setDiscontinuedDate(null);
				}
				
				Context.getOrderService().saveOrder(order);
				
				++importedOrders;
			}
		}
		catch (IncompleteMappingException ex) {
			issues.add(new ImportIssue(patient, "Error while importing drug orders: " + ex.getMessage()));
		}
	}
	
	/**
	 * Gets if task is complete
	 * @return true if task is complete
	 */
	public boolean isCompleted() {
		return (endTime != null);
	}
	
	/**
	 * Gets the import progress
	 * @return the progress (0...100)
	 */
	public float getProgress() {
		if (isCompleted())
			return 100.0f;
		else
			return (totalPatients > 0) ? (100.0f * processedPatients / totalPatients) : 0.0f;
	}
	
	/**
	 * Gets the number of patients imported
	 * @return the number
	 */
	public int getImportedPatients() {
		return importedPatients;
	}

	/**
	 * Gets the number of encounters imported
	 * @return the number
	 */
	public int getImportedEncounters() {
		return importedEncounters;
	}
	
	/**
	 * Gets the number of observations imported
	 * @return the number
	 */
	public int getImportedObservations() {
		return importedObservations;
	}

	/**
	 * Gets the number of orders imported
	 * @return the number
	 */
	public int getImportedOrders() {
		return importedOrders;
	}

	/**
	 * Gets the exception caused by this task, if any
	 * @return the exception
	 */
	public Exception getException() {
		return exception;
	}

	/**
	 * Gets the issues
	 * @return the issues
	 */
	public List<ImportIssue> getIssues() {
		return issues;
	}
	
	/**
	 * Gets time taken by the task. If task is still running then it's the time taken so far.
	 * If task has completed then its the time that was taken to complete
	 * @return the time in seconds
	 */
	public int getTimeTaken() {
		Date end = (endTime != null) ? endTime : new Date(); 
		return (int)((end.getTime() - startTime.getTime()) / 1000);
	}
	
	/**
	 * Gets the entity builder used by this task
	 * @return the entity builder
	 */
	public EntityBuilder getEntityBuilder() {
		return builder;
	}
}
