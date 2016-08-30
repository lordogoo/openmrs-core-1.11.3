/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.web.controller.person;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Person;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.web.dwr.PersonListItem;
import org.springframework.validation.BindException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.SimpleFormController;
import org.springframework.web.servlet.view.RedirectView;

public class AddPersonController extends SimpleFormController {
	
	/** Logger for this class and subclasses */
	protected final Log log = LogFactory.getLog(getClass());
	
	private final String PATIENT_SHORT_EDIT_URL = "/admin/patients/shortPatientForm.form";
	
	private final String PATIENT_EDIT_URL = "/admin/patients/patient.form";
	
	private final String PATIENT_VIEW_URL = "/patientDashboard.form";
	
	private final String USER_EDIT_URL = "/admin/users/user.form";
	
	private final String PERSON_EDIT_URL = "/admin/person/person.form";
	
	private final String FORM_ENTRY_ERROR_URL = "/admin/person/entryError";
	
	private boolean invalidAgeFormat = false;
	
	/**
	 * @see org.springframework.web.servlet.mvc.SimpleFormController#onSubmit(javax.servlet.http.HttpServletRequest,
	 *      javax.servlet.http.HttpServletResponse, java.lang.Object,
	 *      org.springframework.validation.BindException)
	 */
	@Override
	protected ModelAndView onSubmit(HttpServletRequest request, HttpServletResponse response, Object command,
	        BindException errors) throws Exception {
		
		HashMap<String, String> person = getParametersFromRequest(request);
		
		String personId = person.get("personId");
		String viewType = person.get("viewType");
		String personType = person.get("personType");
		
		if ("".equals(personId)) {
			// if they didn't pick a person, continue on to the edit screen no matter what type of view was requsted)
			if ("view".equals(viewType) || "shortEdit".equals(viewType)) {
				viewType = "shortEdit";
			} else {
				viewType = "edit";
			}
			
			return new ModelAndView(new RedirectView(getPersonURL("", personType, viewType, request)));
		} else {
			// if they picked a person, go to the type of view that was requested
			
			// if they selected view, do a double check to make sure that type of person already exists
			if ("view".equals(viewType)) {
				// TODO Do we even want to ever redirect to a 'view'.  I'm torn between jumping the DAs right to the 
				// dashboard or jumping them to the short edit screen to make (potential) adjustments
				if ("patient".equals(personType)) {
					try {
						if (Context.getPatientService().getPatient(Integer.valueOf(personId)) == null) {
							viewType = "shortEdit";
						}
					}
					catch (Exception noPatientEx) {
						// if there is no patient yet, they must go through those motions
						viewType = "shortEdit";
					}
				}
			}
			
			// redirect to the appropriate url
			return new ModelAndView(new RedirectView(getPersonURL(personId, personType, viewType, request)));
		}
	}
	
	/**
	 * This is called prior to displaying a form for the first time. It tells Spring the
	 * form/command object to load into the request
	 *
	 * @see org.springframework.web.servlet.mvc.AbstractFormController#formBackingObject(javax.servlet.http.HttpServletRequest)
	 * @should catch an invalid birthdate
	 * @should catch pass for a valid birthdate
	 */
	@Override
	protected List<PersonListItem> formBackingObject(HttpServletRequest request) throws ServletException {
		
		log.debug("Entering formBackingObject()");
		
		List<PersonListItem> personList = new Vector<PersonListItem>();
		
		if (Context.isAuthenticated()) {
			PersonService ps = Context.getPersonService();
			
			Integer userId = Context.getAuthenticatedUser().getUserId();
			
			invalidAgeFormat = false;
			HashMap<String, String> person = getParametersFromRequest(request);
			
			String gender = person.get("gender");
			String name = person.get("name");
			String birthdate = person.get("birthdate");
			String age = person.get("age");
			
			log.debug("name: " + name + " birthdate: " + birthdate + " age: " + age + " gender: " + gender);
			
			if (!name.equals("") || !birthdate.equals("") || !age.equals("") || !gender.equals("")) {
				
				log.info(userId + "|" + name + "|" + birthdate + "|" + age + "|" + gender);
				
				Integer d = null;
				birthdate = birthdate.trim();
				age = age.trim();
				int birthyear = -1;
				
				try {
					//Do these if there's a value in the birthdate string
					if (birthdate.length() > 0) {
						Date birthdateFormatted = (Date) Context.getDateFormat().parse(birthdate);
						Calendar calender = Calendar.getInstance();
						calender.setTime(birthdateFormatted);
						birthyear = calender.get(Calendar.YEAR);
					}
				}
				catch (ParseException e) {
					// In theory, this should never happen -- the date selector should never allowed the
					// user set an invalid date, but never know the scripts could be broken
					if (log.isDebugEnabled()) {
						log.debug("Parse exception occurred : " + e);
					}
					invalidAgeFormat = true;
				}
				
				// -1 means the birth-year has not defined.
				if (birthyear != -1) {
					d = Integer.valueOf(birthyear);
				} else if (age.length() > 0) {
					Calendar c = Calendar.getInstance();
					c.setTime(new Date());
					d = c.get(Calendar.YEAR);
					try {
						d = d - Integer.parseInt(age);
					}
					catch (NumberFormatException e) {
						// In theory, this should never happen -- Javascript in the UI should prevent this... 
						invalidAgeFormat = true;
					}
				}
				
				if (gender.length() < 1) {
					person.put("gender", null);
				}
				
				personList = new Vector<PersonListItem>();
				for (Person p : ps.getSimilarPeople(name, d, gender)) {
					personList.add(PersonListItem.createBestMatch(p));
				}
			}
			
		}
		
		log.debug("Returning personList of size: " + personList.size() + " from formBackingObject");
		
		return personList;
	}
	
	/**
	 * Prepares the form view
	 */
	public ModelAndView showForm(HttpServletRequest request, HttpServletResponse response, BindException errors)
	        throws Exception {
		
		log.debug("In showForm method");
		
		ModelAndView mav = super.showForm(request, response, errors);
		
		// If a invalid age is submitted, give the user a useful error message.
		if (invalidAgeFormat) {
			mav = new ModelAndView(FORM_ENTRY_ERROR_URL);
			mav.addObject("errorTitle", "Person.age.error");
			mav.addObject("errorMessage", "Person.birthdate.required");
			return mav;
		}
		
		Object o = mav.getModel().get(this.getCommandName());
		
		List personList = (List) o;
		
		log.debug("Found list of size: " + personList.size());
		
		if (personList.size() < 1 && Context.isAuthenticated()) {
			HashMap<String, String> person = getParametersFromRequest(request);
			
			String name = person.get("name");
			String birthdate = person.get("birthdate");
			String age = person.get("age");
			String gender = person.get("gender");
			String viewType = person.get("viewType");
			String personType = person.get("personType");
			
			if (viewType == null) {
				viewType = "edit";
			}
			
			log.debug("name: " + name + " birthdate: " + birthdate + " age: " + age + " gender: " + gender);
			
			if (!name.equals("") || !birthdate.equals("") || !age.equals("") || !gender.equals("")) {
				mav.clear();
				mav.setView(new RedirectView(getPersonURL("", personType, viewType, request)));
			}
		}
		
		return mav;
	}
	
	/**
	 * Returns the url string for the given personType and viewType
	 *
	 * @param personId
	 * @param personType
	 * @param viewType
	 * @param request
	 * @return url string
	 * @throws ServletException
	 * @throws UnsupportedEncodingException
	 */
	private String getPersonURL(String personId, String personType, String viewType, HttpServletRequest request)
	        throws ServletException, UnsupportedEncodingException {
		
		HashMap<String, String> person = getParametersFromRequest(request);
		
		if ("patient".equals(personType)) {
			if ("edit".equals(viewType)) {
				return request.getContextPath() + PATIENT_EDIT_URL + getParametersForURL(person);
			}
			if ("shortEdit".equals(viewType)) {
				return request.getContextPath() + PATIENT_SHORT_EDIT_URL + getParametersForURL(person);
			} else if ("view".equals(viewType)) {
				return request.getContextPath() + PATIENT_VIEW_URL + getParametersForURL(person);
			}
		} else if ("user".equals(personType)) {
			return request.getContextPath() + USER_EDIT_URL + getParametersForURL(person);
		} else {
			if ("edit".equals(viewType)) {
				return request.getContextPath() + PERSON_EDIT_URL + getParametersForURL(person);
			}
		}
		throw new ServletException(
		        "You entered viewType = \""
		                + viewType
		                + "\" and personType = \""
		                + personType
		                + "\" which is an invalid viewType/personType combination.\n"
		                + "Valid viewType/personType combinations are edit/patient, edit/user, shortEdit/patient, view/patient. The viewType edit is valid without any personType. Also, the personType user is valid without any viewType. \n");
	}
	
	/**
	 * Returns the appropriate ?patientId/?userId/?name&age&birthyear etc
	 *
	 *
	 * @param person@return
	 * @throws UnsupportedEncodingException
	 */
	private String getParametersForURL(HashMap<String, String> person) throws UnsupportedEncodingException {
		
		if ("".equals(person.get("personId"))) {
			return "?addName=" + URLEncoder.encode(person.get("name"), "UTF-8") + "&addBirthdate=" + person.get("birthdate")
			        + "&addAge=" + person.get("age") + "&addGender=" + person.get("gender");
		} else {
			if ("patient".equals(person.get("personType"))) {
				return "?patientId=" + person.get("personId");
			} else if ("user".equals(person.get("personType"))) {
				return "?userId=" + person.get("personId");
			} else {
				return "?personId=" + person.get("personId");
			}
		}
	}
	
	/**
	 * @param request
	 */
	private HashMap<String, String> getParametersFromRequest(HttpServletRequest request) {
		HashMap<String, String> person = new HashMap<String, String>();
		person.put("name", ServletRequestUtils.getStringParameter(request, "addName", ""));
		person.put("birthdate", ServletRequestUtils.getStringParameter(request, "addBirthdate", ""));
		person.put("age", ServletRequestUtils.getStringParameter(request, "addAge", ""));
		person.put("gender", ServletRequestUtils.getStringParameter(request, "addGender", ""));
		person.put("personType", ServletRequestUtils.getStringParameter(request, "personType", "patient"));
		person.put("personId", ServletRequestUtils.getStringParameter(request, "personId", ""));
		person.put("viewType", ServletRequestUtils.getStringParameter(request, "viewType", ""));
		
		return person;
	}
}
