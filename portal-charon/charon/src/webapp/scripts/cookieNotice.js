// Module Pattern
var COOKIE_NOTICE = (function ($) {

    // Private variables
    var cookieNotice = {};
    var cookieName = "dontShowCookieNotice";

    // Private methods
    var displayCookieNotice = function () {

        // Notice and message
        var cookieNoticeText = 'We use cookies to help give you the best experience on our website. By continuing without changing your cookie settings, we assume you agree to this. ' +
            'Please read our ' + '<a href="https://blogs.it.ox.ac.uk/adamweblearn/">' + 'cookie policy' + '</a>' + ' to find out more.';

        $('body').prepend('<div id="cookie-notice"><div class="cookie-notice-content"><p class="cookie-notice-text">' + cookieNoticeText + '</p><a href="#" id="cookie-notice-close">' +
            'Dismiss' + '</a></div></div>');

        // Close button - delegated click event
        $(document).delegate('#cookie-notice-close', 'click', function() {
            $("#cookie-notice").slideUp("slow");
        });

        $("#cookie-notice").slideDown("slow");

        // Set notice to never show again by default. Unnecessary to show every page load, particularly if we're suggesting showing it once is considered as acceptance.
        dontShowCookieNoticeAgain();
    }

    var dontShowCookieNoticeAgain = function () {
        // set or extend the cookie life for a year
        var exdate = new Date();
        exdate.setDate(exdate.getDate() + 365);
        document.cookie = cookieName + "=" + "TRUE; expires=" + exdate.toUTCString() + ";domain=" + document.domain + ";path=/";
    }

    var pleaseShowCookieNotice = function () {
        // Don't show the notice if we have previously set a cookie to hide it
        var dontShowCookieNotice = (document.cookie.indexOf(cookieName) != -1) ? false : true;
        return dontShowCookieNotice;
    }

    // Public methods
    cookieNotice.showCookieNotice = function () {
        if (pleaseShowCookieNotice() == true) {
            displayCookieNotice();
        }
    }

    return cookieNotice;

}(jQuery));


$(document).ready(function() {
    COOKIE_NOTICE.showCookieNotice();
});
