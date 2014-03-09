// window.plugins.pushNotification

function PushNotification() {
}

PushNotification.prototype.registerForNotifications = function(config, success, fail) {
	cordova.exec(success, fail, "PushNotification", "registerForNotifications", config ? [config] : []);
};

PushNotification.prototype.unregisterForNotifications = function(config, success, fail) {
	cordova.exec(success, fail, "PushNotification", "unregisterForNotifications", config ? [config] : []);
};

PushNotification.prototype.onDeviceReady = function() {
	cordova.exec(null, null, "PushNotification", "onDeviceReady", []);
};

PushNotification.prototype.notificationCallback = function(notification) {
	var ev = document.createEvent('HTMLEvents');
	ev.notification = notification;
	ev.initEvent('push-notification', true, true, arguments);
	document.dispatchEvent(ev);
};

PushNotification.prototype.registerDeviceCallback = function(deviceInfo) {
	var ev = document.createEvent('HTMLEvents');
	ev.deviceInfo = deviceInfo;
	ev.initEvent('register-for-notifications', true, true, arguments);
	document.dispatchEvent(ev);
};

cordova.addConstructor(function()  {
	if(!window.plugins) {
	   window.plugins = {};
	}

   // shim to work in 1.5 and 1.6
   if (!window.Cordova) {
	   window.Cordova = cordova;
   };

   window.plugins.pushNotification = new PushNotification();
});