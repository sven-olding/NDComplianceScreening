package com.gi.crm.muehlbauer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gi.crm.tools.SessionProvider;
import com.gi.crm.tools.Tools;

import lotus.domino.Document;
import lotus.domino.NotesException;

public class ComplianceScreening
{
	private String	clientIdentCode;
	private String	profileIdentCode;
	private String	clientSystemId;
	private String	apiUser;
	private String	apiPassword;
	private String	endpointBaseUrl;
	private double	threshold	= 80.0;

	public ComplianceScreening() throws NotesException
	{
		Document config = SessionProvider.getSession().getCurrentDatabase().getProfileDocument("gesetkeywords",
				"muehlbauer");
		@SuppressWarnings("unchecked")
		Vector<String> configVals = config.getItemValue("cuComplianceScreeningParams");
		Iterator<String> it = configVals.iterator();
		while (it.hasNext()) {
			String configVal = it.next();
			String[] split = configVal.split("\\|");
			String displayValue = split[0];
			String value = split[1];
			switch (displayValue) {
				case "clientIdentCode":
					clientIdentCode = value;
					break;
				case "profileIdentCode":
					profileIdentCode = value;
					break;
				case "clientSystemId":
					clientSystemId = value;
					break;
				case "apiUser":
					apiUser = value;
					break;
				case "apiPassword":
					apiPassword = value;
					break;
				case "endpointBaseUrl":
					endpointBaseUrl = value;
					break;
				case "threshold":
					threshold = Double.valueOf(value);
					break;
				default:
					break;
			}
		}
	}

	public String getAuthorizationHeaderValue()
	{
		String str = apiUser + "@" + clientIdentCode + ":" + apiPassword;
		return "Basic " + Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
	}

	public String getComplianceScreeningObject(String addressType, String name, String street, String zip, String city,
			String countryCode, String phone, String mail, String fax, String ref)
			throws NotesException, JsonProcessingException
	{
		HashMap<String, Object> obj = new HashMap<String, Object>();

		HashMap<String, Object> addr = new HashMap<String, Object>();

		addr.put("addressType", addressType);
		addr.put("name", name);
		addr.put("street", street);
		addr.put("pc", zip);
		addr.put("city", city);
		addr.put("countryISO", countryCode);
		addr.put("telNo", phone);
		addr.put("email", mail);
		addr.put("fax", fax);
		addr.put("referenceId", ref);

		obj.put("address", addr);

		obj.put("screeningParameters", getScreeningParams());

		ObjectMapper mapper = new ObjectMapper();

		return mapper.writeValueAsString(obj);
	}

	public String getComplianceScreeningObject(Document doc) throws NotesException, JsonProcessingException
	{
		HashMap<String, Object> obj = new HashMap<String, Object>();

		HashMap<String, Object> addr = new HashMap<String, Object>();
		boolean isCompany = doc.getItemValueString("DocType").equalsIgnoreCase("Company");

		addr.put("addressType", isCompany ? "entity" : "individual");
		addr.put("name", isCompany ? doc.getItemValueString("Company")
				: (doc.getItemValueString("FirstName") + " " + doc.getItemValueString("LastName")).trim());
		addr.put("street", doc.getItemValueString("Address1"));
		addr.put("pc", doc.getItemValueString("ZipCode"));
		addr.put("city", doc.getItemValueString("City"));
		addr.put("countryISO", Tools.word(doc.getItemValueString("CountryToo"), "^", 8).toUpperCase());
		addr.put("telNo", isCompany ? doc.getItemValueString("Phone") : doc.getItemValueString("MainPhone"));
		addr.put("email", doc.getItemValueString("EMailAddress"));
		addr.put("fax", doc.getItemValueString("Fax"));
		addr.put("referenceId", doc.getItemValueString("fdMe"));

		obj.put("address", addr);

		obj.put("screeningParameters", getScreeningParams());
		ObjectMapper mapper = new ObjectMapper();

		return mapper.writeValueAsString(obj);
	}

	public HashMap<String, Object> getScreeningParams() throws NotesException
	{
		HashMap<String, Object> params = new HashMap<String, Object>();

		params.put("clientIdentCode", clientIdentCode);
		params.put("profileIdentCode", profileIdentCode);
		params.put("threshold", new Double(threshold).longValue());
		params.put("clientSystemId", clientSystemId);
		params.put("suppressLogging", true);
		params.put("considerGoodGuys", true);
		params.put("userIdentification", SessionProvider.getSession().getEffectiveUserName());
		params.put("addressTypeVersion", "1");

		return params;
	}

	public String getEndpointBaseUrl()
	{
		return endpointBaseUrl;
	}

	public double getThreshold()
	{
		return threshold;
	}

}
