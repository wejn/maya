# What's this
This WMS 3.X+ module allows you to manage wowza applications via HTTP;
you can think of it as more powerful sister of
[Rhea](http://wejn.com/blog/2013/08/introducing-rhea/).

# Installation
Add `wejn-maya.jar` to Wowza's `lib/` directory.

Add following to appropriate HTTPProviders section of your VHost.xml
(make sure you're adding before all `HTTPProvider`s with
`RequestFilters` set to `*`):

```xml
<HTTPProvider>
	<BaseClass>cz.wejn.maya.Provider</BaseClass>
	<RequestFilters>maya*</RequestFilters>
	<AuthenticationMethod>maya</AuthenticationMethod>
</HTTPProvider>
```

Then add following to Authentication/Methods in your Authentication.xml:

```xml
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
```

If you're running Wowza 4.X, chances are you're missing the
`conf/Authentication.xml` file altogether. Following guide illustrates
[what to do when you're missing conf/Authentication.xml in Wowza Streaming Engine 4.X](http://wejn.com/blog/2014/06/wowza-streaming-engine-missing-conf-slash-authentication-dot-xml/).

And finally add `username password` lines (containing your real
username/password pairs) to your
`${com.wowza.wms.context.VHostConfigHome}/conf/maya.password`.

Restart Wowza to finish installation.

# ... for the rest of the documentation ...
Please see [the auto-generated documentation on my site]<http://wejn.com/downloads/readme-maya.html> or the plain `readme-maya.txt` in this repo.

I'm not exactly fanboy of Markdown and don't feel like converting eveything
by hand.

# License
Copyright (c) 2014 Michal "Wejn" Jirku <box@wejn.org>

This work is licensed under the Creative Commons Attribution 3.0 Czech Republic License. To view a copy of this license, visit [http://creativecommons.org/licenses/by/3.0/cz/](http://creativecommons.org/licenses/by/3.0/cz/).
