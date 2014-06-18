package cz.wejn._common;

import com.wowza.wms.http.*;
import java.util.*;

public class RequestUtils {
	/**
	 * Returns Map of Accept-able Content-Types with weights (0..1000).
	 *
	 * @param req http request
	 * @return Map which maps given Content-Types to preferences (0..1000)
	 */
	public static Map<String,Integer> getContentTypes(IHTTPRequest req) {
		String accept = req.getHeader("Accept");
		Map<String, Integer> out = new HashMap<String,Integer>();

		if (accept == null) {
			return out;
		}

		String[] fields = accept.split(",");

		for (String field: fields) {
			String[] data = field.trim().split(";");
			int q = 1000;
			if (data.length >= 2) {
				for (int i = 1; i < data.length; i++) {
					String str_q = data[i].trim();
					if (str_q.matches("^q=.*")) {
						if (str_q.matches("^q=(1(\\.0{1,3})?|0\\.[0-9]{1,3})$")) {
							try {
								q = (int) (Double.valueOf(str_q.substring(2)) * 1000);
							} catch (Exception e) {
								q = 0;
							}
						} else {
							q = 0;
						}
					}
				}
			}

			out.put(data[0], q);
		}

		return out;
	}

	public static boolean prefersContentTypeOver(IHTTPRequest req, String ct, String other) {
		Map<String,Integer> types = getContentTypes(req);
		int pref = 0;
		int oth = 0;

		if (types.containsKey(ct)) {
			pref = types.get(ct);
		}

		if (types.containsKey(other)) {
			oth = types.get(other);
		}

		return pref > 0 && pref >= oth;
	}
}
