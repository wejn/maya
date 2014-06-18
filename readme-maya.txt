! Maya module
= What's this
This WMS 3.X+ module allows you to manage wowza applications via HTTP;
you can think of it as more powerful sister of
Rhea<http://wejn.com/blog/2013/08/introducing-rhea/>.

As for the name, it's derived from: ^ma^nage ^y^our ^a^pps.

= Installation
Add `wejn-maya.jar` to Wowza's `lib/` directory.

Add following to appropriate HTTPProviders section of your VHost.xml
(make sure you're adding before all `HTTPProvider`s with
`RequestFilters` set to `*`):

{{{xml
<HTTPProvider>
	<BaseClass>cz.wejn.maya.Provider</BaseClass>
	<RequestFilters>maya*</RequestFilters>
	<AuthenticationMethod>maya</AuthenticationMethod>
</HTTPProvider>
}}}

Then add following to Authentication/Methods in your Authentication.xml:

{{{xml
<Method>
	<Name>maya</Name>
	<Description>Maya Authentication</Description>
	<Class>com.wowza.wms.authentication.AuthenticateBasic</Class>
	<!-- XXX: for digest auth use AuthenticateDigest above -->
	<Properties>
		<Property>
			<Name>passwordFile</Name>
			<Value>${com.wowza.wms.context.VHostConfigHome}/conf/maya.password</Value>
		</Property>
		<Property>
			<Name>realm</Name>
			<Value>Maya</Value>
		</Property>
	</Properties>
</Method>
}}}

If you're running Wowza 4.X, chances are you're missing the
`conf/Authentication.xml` file altogether. Following guide illustrates
"what to do when you're missing conf/Authentication.xml in Wowza Streaming Engine 4.X"<http://wejn.com/blog/2014/06/wowza-streaming-engine-missing-conf-slash-authentication-dot-xml/>.

And finally add `username password` lines (containing your real
username/password pairs) to your
`${com.wowza.wms.context.VHostConfigHome}/conf/maya.password`.

Restart Wowza to finish installation.

= Configuration options, JMX interface
None.

= API
Maya's API is straightforward. You GET/POST a request with several
parameters and get back either `text/plain` or `application/json`,
depending on your preference.

|| Parameter | Type | Description
| op | optional | operation to perform, see <#Operations>, defaults to `shutdown`
| app | mandatory for some ops | application to affect
| format | optional | output format (`plain`, `json`), overrides `Accept:` header

As the table above suggests, you either send proper `Accept` header,
or you override with `format` parameter. If you omit `format` parameter
and don't specify `application/json` with higher priority than `text/plain`,
default output will be `text/plain`. Reason for this (just as with default
`op` being `shutdown`) is backward compatibility with Rhea<http://wejn.com/blog/2013/08/introducing-rhea/>.

== Response types
=== plain
Always contains textual description of what happened, response code
(and `ERROR` prefix in body) indicates error.

=== json
Always contains JSON-serialized object.

All responses share following keys:
: `error` = set to true if error, false otherwise
: `code` = copy of http response code

Errors have non-200 response code and additional `message` key.

Successes have 200 response code and additional `response` key
whose value depends on requested operation.

== Operations
Following is exhaustive list of supported operations:

=== getinstalled
Returns list of installed applications. That is, applications that
have `conf/APPNAME/Application.xml` file present.

Neither requires or uses `app` parameter.
=== getenabled
Returns list of enabled applications. That is, applications that
have `application/APPNAME` directory entry.

Neither requires or uses `app` parameter.
=== getactive
Returns list of currently loaded applications.

Neither requires or uses `app` parameter.
=== init
Starts given application (will succeed if this app is installed).

Returns `OK` if successful (app loaded).

=== shutdown
Shuts given application down.

Returns `OK` if successful (app terminated).

=== enable
Enables given application (either renames `applications.disabled/APPNAME`
as `applications/APPNAME` or creates a new one).

Please note this does NOT start the application.

Returns `OK` if successful (app enabled).

Please see <#Note about enabling/disabling applications> below.

=== disable
Disables given application (renames `applications/APPNAME`
as `applications.disabled/APPNAME`).

Application is terminated (the same way <#shutdown> op would do it)
before it gets disabled.

Returns `OK` if successful (app enabled).

Will fail to disable application if `applications.disabled/APPNAME`
already exists.

Please see <#Note about enabling/disabling applications> below.

== Note about enabling/disabling applications
I fully realize that this way of enabling/disabling applications 
has at least two drawbacks:

First of all, if you happen to use Wowza's mass config (where Wowza
loads configuration via HTTP requests) this will NOT work for you
at all. Sorry. You're welcome to send a diff, though.

Second of all, it's opinionated way of managing applications.

My reasoning for sticking with simple `rename` call is that recursively
deleting directories (especially when running under root [blech]) is not
exactly task to be undertaken via Java file operations. As it could easily
lead to security bugs I'm not prepared to be responsible for.

That's why this enable/disable sticks with basics (`rename` call)
which should (hopefully) work safely on both Linux and Windows
platforms.

If you want to convince me of a better approach, please do by sending
in a diff. Or just a nice technical email outlining better solution.

== Example
Let's say our wowza is running at `wms-dev.wejn.com` and has
multiple applications, one of which is `origin`.

Maya is installed in the first `<HostPort>` block with port `1935`.

Everything else is set to default.

To terminate application named `origin` one would call:

`http://wms-dev.wejn.com:1935/maya?app=origin`

which would yield response: `OK` when the application was terminated
successfully and `ERROR: <explanation>` in case of failure.

Also, calling:

`http://wms-dev.wejn.com:1935/maya?format=json&op=getinstalled`

should yield response similar to:

{{{json
{
  "response" : [ "mist", "vod-gta", "vod", "vod-kala", "live2", "iprima-cs",
    "live", "restream", "auth-test", "origin", "trans", "edge" ],
  "error" : false,
  "code" : 200
}
}}}

= License
Copyright (c) 2014 Michal "Wejn" Jirku <box@wejn.org>

This work is licensed under the Creative Commons Attribution 3.0 Czech Republic License. To view a copy of this license, visit "http://creativecommons.org/licenses/by/3.0/cz/"<http://creativecommons.org/licenses/by/3.0/cz/>.
