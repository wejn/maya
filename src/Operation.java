package cz.wejn.maya;

enum Operation {
	GETINSTALLED(false),
	GETENABLED(false),
	GETACTIVE(false),
	INIT,
	SHUTDOWN,
	ENABLE,
	DISABLE;

	protected boolean need_app = false;

	public boolean needApp() {
		return this.need_app;
	}

	Operation() {
		this(true);
	}

	Operation(boolean need_app) {
		this.need_app = need_app;
	}
}
