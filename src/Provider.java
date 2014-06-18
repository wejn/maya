package cz.wejn.maya;

import java.util.*;
import java.io.*;
import com.wowza.wms.application.*;
import com.wowza.wms.http.*;
import com.wowza.wms.logging.*;
import com.wowza.wms.stream.*;
import com.wowza.wms.vhost.*;
import edu.emory.mathcs.backport.java.util.concurrent.locks.*;
import com.wowza.wms.httpstreamer.model.*;
import com.wowza.wms.rtp.model.*;
import com.wowza.wms.client.*;
import com.wowza.wms.mediacaster.*;
import java.util.concurrent.Callable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import cz.wejn._common.RequestUtils;

public class Provider extends HTTProvider2Base
{
	private static final String DISABLED_APPS = "applications.disabled";
	private static final String ENABLED_APPS = "applications";
	private static final String CONFIG_DIR = "conf";
	private static final String APP_CONFIG_FILE = "Application.xml";

	private WMSLogger logger = null;

	public Provider() {
		this.logger = WMSLoggerFactory.getLogger(this.getClass());
	}

	@Override
	public void onHTTPRequest(IVHost v, IHTTPRequest ireq, IHTTPResponse resp) {
		if (!doHTTPAuthentication(v, ireq, resp)) {
			return;
		}

		Request req = null;

		// Get & validate request
		
		try {
			req = Request.fromIHR(ireq, resp);
		} catch (IllegalArgumentException e) {
			sendError(_emergencyRequest(ireq, resp), 400, e.getMessage());
			return;
		} catch (Exception e) {
			sendError(_emergencyRequest(ireq, resp), 500, e.toString());
			return;
		}

		// Perform actual work

		try {
			switch (req.op) {
				case INIT:
					if (v.isApplicationLoaded(req.app) || v.startApplicationInstance(req.app)) {
						sendResult(req, "OK");
					} else {
						sendError(req, 500, "couldn't load application: " + req.app);
					}
					break;
				case SHUTDOWN:
					if (! v.isApplicationLoaded(req.app)) {
						sendResult(req, "OK");
					} else {
						shutdownApp(v, req.app);
						sendResult(req, "OK");
					}
					break;
				case ENABLE:
					if (v.applicationExists(req.app)) {
						sendResult(req, "OK");
					} else {
						if (installedApp(v, req.app)) {
							if (enableApp(v, req.app)) {
								sendResult(req, "OK");
							} else {
								sendError(req, 500, "couldn't enable application, try again");
							}
						} else {
								sendError(req, 403, "won't enable non-installed application");
						}
					}
					break;
				case DISABLE:
					if (! v.applicationExists(req.app)) {
						sendResult(req, "OK");
					} else {
						if (shutdownAndDisableApp(v, req.app)) {
							sendResult(req, "OK");
						} else {
							sendError(req, 500, "couldn't disable application, try again");
						}
					}
					break;
				case GETENABLED:
					sendResult(req, v.getApplicationFolderNames());
					break;
				case GETACTIVE:
					sendResult(req, v.getApplicationNames());
					break;
				case GETINSTALLED:
					sendResult(req, getInstalledApps(v));
					break;
				default:
					sendError(req, 500, "not implemented");
					break;
			}
		} catch (Exception e) {
			sendError(req, 500, "got exception: " + e.toString());
		}
	}

	// Response processing functions

	protected void sendResponse(Request req, int code, Object data) {
		try {
			req.resp.setResponseCode(code);
			OutputStream out = req.resp.getOutputStream();
			switch (req.fmt) {
				case JSON:
					req.resp.setHeader("Content-Type", "application/json");
					Map<String, Object> fields = new HashMap<String, Object>();
					fields.put("code", code);
					fields.put("error", code != 200);
					if (code != 200) {
						fields.put("message", data);
					} else {
						fields.put("response", data);
					}
					ObjectMapper mapper = new ObjectMapper();
					mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
					mapper.writeValue(out, fields);

					break;
				default:
					// FALL THRU
				case PLAIN:
					req.resp.setHeader("Content-Type", "text/plain");
					byte[] outBytes = null;
					if (code != 200) {
						outBytes = ("ERROR: " + data.toString()).getBytes();
					} else {
						outBytes = data.toString().getBytes();
					}
					out.write(outBytes);
					break;
			}
					
		} catch (Exception e) {
			this.logger.error("Maya#Provider::" + req.toString() + ": can't send response: " + e.toString());
		}
	}

	protected void sendResult(Request req, Object result) {
		this.logger.info("Maya#Provider::" + req.toString() + ": " + _toJSON(result));
		sendResponse(req, 200, result);
	}

	protected void sendError(Request req, int code, String message) {
		this.logger.error("Maya#Provider::" + req.toString() + ": " + code + " / " + message);
		sendResponse(req, code, message);
	}

	// Helper functions
	
	protected Request _emergencyRequest(IHTTPRequest req, IHTTPResponse resp) {
		ResponseFormat fmt = ResponseFormat.PLAIN;
		try {
			// try fetching output format
			Map<String, List<String>> params = req.getParameterMap();
			String str_fmt = params.get("format").get(0).trim().toUpperCase();
			fmt = ResponseFormat.valueOf(str_fmt);
		} catch (Exception e) {
			// ok, no dice with param, try Accept: header
			if (RequestUtils.prefersContentTypeOver(req, "application/json", "text/plain")) {
				fmt = ResponseFormat.JSON;
			}
		}
		return new Request(null, null, req, resp, fmt);
	}
	
	protected String _toJSON(Object obj) {
		try {
			return new ObjectMapper().writeValueAsString(obj);
		} catch (Exception e) {
			return "exception: " + e.toString();
		}
	}

	protected File _configDir(IVHost v) {
		return new File(v.getHomePath() + "/" + CONFIG_DIR);
	}

	protected File _disabledAppDir(IVHost v, String app) {
		if (app == null) {
			return new File(v.getHomePath() + "/" + DISABLED_APPS);
		} else {
			return new File(v.getHomePath() + "/" + DISABLED_APPS + "/" + app);
		}
	}

	protected File _appDir(IVHost v, String app) {
		if (app == null) {
			return new File(v.getHomePath() + "/" + ENABLED_APPS);
		} else {
			return new File(v.getHomePath() + "/" + ENABLED_APPS + "/" + app);
		}
	}

	// Actual worker functions
	
	protected boolean installedApp(IVHost v, String param_app) {
		// XXX: Yes, we can do better than O(n); no, not now
		return getInstalledApps(v).contains(param_app);
	}

	protected List<String> getInstalledApps(IVHost v) {
		List<String> out = new ArrayList<String>();
		for (File entry: _configDir(v).listFiles()) {
			File appconfig = new File(entry, APP_CONFIG_FILE);
			if (entry.exists() && entry.isDirectory() && appconfig.exists()) {
				out.add(entry.getName());
			}
		}

		return out;

	}

	protected boolean enableApp(IVHost v, String param_app) throws Exception {
		File dad = _disabledAppDir(v, param_app);
		File ad = _appDir(v, param_app);

		if (ad.exists()) {
			return true;
		} else {
			if (dad.exists()) {
				return dad.renameTo(ad);
			} else {
				return ad.mkdir();
			}
		}
	}

	protected boolean shutdownAndDisableApp(IVHost v, String param_app) throws Exception {
		final Provider provider = this;
		final IVHost vhost = v;
		final String app = param_app;
		return shutdownApp(v, param_app, new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				return provider.disableApp(vhost, app);
			}
		});
	}

	protected boolean disableApp(IVHost v, String param_app) throws Exception {
		File dadir = _disabledAppDir(v, null);
		File dad = _disabledAppDir(v, param_app);
		File ad = _appDir(v, param_app);

		if (! ad.exists()) {
			return true;
		} else {
			if (dad.exists()) {
				throw new RuntimeException("app dir exists and so does disabled app dir");
			} else {
				if (! dadir.exists()) {
					dadir.mkdir();
					if (! dadir.exists()) {
						throw new RuntimeException("disabled app dir can't be created");
					}
				}
				return ad.renameTo(dad);
			}
		}
	}

	protected void shutdownApp(IVHost v, String param_app) throws Exception {
		shutdownApp(v, param_app, null);
	}

	protected <T> T shutdownApp(IVHost v, String param_app, Callable<T> cb) throws Exception {
		WMSReadWriteLock appLock = v.getApplicationLock();
		WMSReadWriteLock cLock = null;
		appLock.writeLock().lock();
		try {
			IApplication app = v.getApplication(param_app);
			if (app != null) {
				for (String sai: app.getAppInstanceNames()) {
					IApplicationInstance ai = app.getAppInstance(sai);
					cLock = ai.getClientsLockObj();
					cLock.writeLock().lock();

					for (IClient c : ai.getClients()) {
						c.setShutdownClient(true);
					}

					for (IHTTPStreamerSession hss : ai.getHTTPStreamerSessions()) {
						hss.rejectSession();
					}

					for (RTPSession rs : ai.getRTPSessions()) {
						rs.rejectSession();
					}

					MediaCasterStreamMap mcsm = ai.getMediaCasterStreams();
					if (mcsm != null) {
						mcsm.shutdown(true);
					}

					app.removeAppInstance(app.getAppInstance(sai));

					cLock.writeLock().unlock();
					cLock = null;
				}
				app.shutdown(true);
			}

			if (cb != null) {
				return cb.call();
			} else {
				return null;
			}
		} catch (Exception e) {
			throw new Exception("couldn't kill application '" + param_app + "': " + e.getMessage());
		} finally {
			if (cLock != null) {
				cLock.writeLock().unlock();
			}
			appLock.writeLock().unlock();
		}
	}
}
