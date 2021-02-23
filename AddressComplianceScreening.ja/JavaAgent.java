import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gi.crm.log.SimpleLog;
import com.gi.crm.muehlbauer.ComplianceScreening;
import com.gi.crm.tools.Tools;

import lotus.domino.AgentBase;
import lotus.domino.Base;
import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public class JavaAgent extends AgentBase
{
	private Session			session		= null;
	private SimpleLog		log			= null;
	private ArrayList<Base>	recycleList	= new ArrayList<Base>();
	private Database		contactsDb	= null;
	private View			contactView	= null;
	private Document		contactDoc	= null;

	@Override
	public void NotesMain()
	{

		try {
			session = getSession();
			initLog();

			ComplianceScreening cs = new ComplianceScreening();

			log.log(cs.getEndpointBaseUrl());
			log.log(cs.getAuthorizationHeaderValue());

			ObjectMapper objectMapper = new ObjectMapper();

			URL csUrl = new URL(cs.getEndpointBaseUrl() + "/findMatchingAddresses");

			String path[] = Tools.getDatabasePath("Contacts");

			contactsDb = session.getDatabase(path[0], path[1], false);
			recycleList.add(contactsDb);

			contactView = contactsDb.getView("vaCuComplianceScreening");
			recycleList.add(contactView);

			contactView.setAutoUpdate(false);

			contactDoc = contactView.getFirstDocument();
			Document tempDoc = null;
			while (contactDoc != null) {
				tempDoc = contactView.getNextDocument(contactDoc);

				log.log("Checking address: " + contactDoc.getItemValueString("fdMe") + " / "
						+ (contactDoc.getItemValueString("Form").equals("Company")
								? contactDoc.getItemValueString("Company") : contactDoc.getItemValueString("Contact")));

				String requestBody = cs.getComplianceScreeningObject(contactDoc);
				log.log(requestBody);

				HttpURLConnection conn = null;
				try {
					conn = (HttpURLConnection) csUrl.openConnection();
					conn.setRequestMethod("POST");
					conn.addRequestProperty("Accept", "application/json");
					conn.addRequestProperty("Content-Type", "application/json");
					conn.addRequestProperty("Authorization", cs.getAuthorizationHeaderValue());

					conn.setDoOutput(true);
					OutputStream os = conn.getOutputStream();
					byte[] input = requestBody.getBytes("utf-8");
					os.write(input, 0, input.length);

					conn.connect();

					int status = conn.getResponseCode();
					log.log("HTTP Status: " + status);
					if (status == 200) {
						String response = getStringFromReader(new InputStreamReader(conn.getInputStream(), "utf-8"));

						@SuppressWarnings("unchecked")
						List<Map<String, Object>> result = objectMapper.readValue(response, List.class);
						if (result.isEmpty()) {
							log.log("Ok, no match found");
						} else {
							log.logWarning("Match found!");
							log.log(result.get(0).toString());
						}

					} else if (status == 500) {
						String msg = getStringFromReader(new InputStreamReader(conn.getErrorStream(), "utf-8"));
						log.logError(msg);
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
					log.logException(e);
				} finally {
					if (conn != null) {
						try {
							conn.disconnect();
						} catch (Exception e) {
							// ignore
						}
					}
				}

				contactDoc.recycle();
				contactDoc = tempDoc;
			}

		} catch (Exception e) {
			e.printStackTrace();

			if (log != null) {
				log.logException(e);
			}
		} finally {
			if (contactView != null) {
				try {
					contactView.refresh();
					contactView.setAutoUpdate(true);
				} catch (NotesException e) {
					// ignore
				}
			}
			for (Base o : recycleList) {
				try {
					if (o != null) {
						o.recycle();
					}
				} catch (Exception e) {
				}
			}
			closeLog();
		}
	}

	private String getStringFromReader(Reader is)
	{
		BufferedReader br = new BufferedReader(is);
		StringBuilder sb = new StringBuilder();
		String ret = "";
		try {
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line + "\n");
			}
			ret = sb.toString();
		} catch (IOException e) {
			e.printStackTrace();
			log.logException(e);
			ret = "";
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
		return ret;
	}

	private void closeLog()
	{
		if (log != null) {
			log.addNewLine();
			log.log("ENDE Transfer");
			log.close();
		}
	}

	private void initLog() throws NotesException
	{
		log = new SimpleLog(session.getCurrentDatabase(), "Compliance Screening",
				session.getCurrentDatabase().getTitle(), session.getEffectiveUserName());

		log.log("START Transfer");
		log.addNewLine();
	}
}