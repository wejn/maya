package cz.wejn.maya;

import java.util.*;
import com.wowza.wms.http.*;
import cz.wejn._common.RequestUtils;

class Request {
	public Operation op;
	public String app;
	public IHTTPRequest req;
	public IHTTPResponse resp;
	public ResponseFormat fmt;

	public Request(Operation op, String app, IHTTPRequest req, IHTTPResponse resp) {
		this(op, app, req, resp, ResponseFormat.PLAIN);
	}

	public Request(Operation op, String app, IHTTPRequest req, IHTTPResponse resp, ResponseFormat fmt) {
		this.op = op;
		this.app = app;
		this.req = req;
		this.resp = resp;
		this.fmt = fmt;
	}

	public static Request fromIHR(IHTTPRequest req, IHTTPResponse resp) throws IllegalArgumentException {
		Map<String, List<String>> params = req.getParameterMap();
		String param_app = null;
		Operation param_op = Operation.SHUTDOWN;
		ResponseFormat param_fmt = ResponseFormat.PLAIN;

		if (! req.getMethod().toLowerCase().matches("(get|post)")) {
			throw new IllegalArgumentException("unsupported HTTP method");
		}

		if (params.containsKey("op")) {
			String op_str = params.get("op").get(0).trim().toUpperCase();
			try {
				param_op = Operation.valueOf(op_str);
			} catch (Exception e) {
				throw new IllegalArgumentException("invalid 'op' parameter: " + e.getMessage());
			}
		}

		if (param_op.needApp()) {
			if (! params.containsKey("app")) {
				throw new IllegalArgumentException("need 'app' parameter.");
			}

			param_app = params.get("app").get(0);

			if (! param_app.matches("[a-zA-Z0-9_-]+")) {
				throw new IllegalArgumentException("invalid app name, try /[a-zA-Z0-9_-]+/");
			}
		}

		if (params.containsKey("format")) {
			String fmt_str = params.get("format").get(0).trim().toUpperCase();
			try {
				param_fmt = ResponseFormat.valueOf(fmt_str);
			} catch (Exception e) {
				throw new IllegalArgumentException("invalid 'format' parameter: " + e.getMessage());
			}
		} else {
			if (RequestUtils.prefersContentTypeOver(req, "application/json", "text/plain")) {
				param_fmt = ResponseFormat.JSON;
			}
		}

		return new Request(param_op, param_app, req, resp, param_fmt);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append("ip=" + (this.req != null ? this.req.getRemoteAddr() : "unknown"));
		sb.append(", op=" + op);
		sb.append(", app=" + app);
		sb.append(", format=" + fmt);
		sb.append("]");
		return sb.toString();
	}
}
