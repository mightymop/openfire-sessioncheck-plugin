<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.openfire.*,
                 org.igniterealtime.openfire.plugin.sessioncheck.SessionCheckPlugin"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<html>
<head>
<title><fmt:message key="sessioncheck.settings.title"/></title>
<meta name="pageID" content="server-sessioncheck"/>
</head>
<body>

<%  // Get parameters:
    boolean update = request.getParameter("update") != null;
    boolean test = request.getParameter("test") != null;

    boolean sessioncheckEnabled = ParamUtils.getParameter(request,"sessioncheckEnabled")!=null&&ParamUtils.getParameter(request,"sessioncheckEnabled").equals("on")?true:false;
    int sessioncheckInterval = ParamUtils.getParameter(request,"sessioncheckInterval")!=null&&ParamUtils.getParameter(request,"sessioncheckInterval").trim().length()>0?Integer.parseInt(ParamUtils.getParameter(request,"sessioncheckInterval")):60;
    int sessioncheckRestartInterval = ParamUtils.getParameter(request,"sessioncheckRestartInterval")!=null&&ParamUtils.getParameter(request,"sessioncheckRestartInterval").trim().length()>0?Integer.parseInt(ParamUtils.getParameter(request,"sessioncheckRestartInterval")):-1;
    int sessioncheckMin = ParamUtils.getParameter(request,"sessioncheckMin")!=null&&ParamUtils.getParameter(request,"sessioncheckMin").trim().length()>0?Integer.parseInt(ParamUtils.getParameter(request,"sessioncheckMin")):3;
    boolean debuglogEnabled = ParamUtils.getParameter(request,"debuglogEnabled")!=null&&ParamUtils.getParameter(request,"debuglogEnabled").equals("on")?true:false;

    Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    String csrfParam = ParamUtils.getParameter(request, "csrf");

    if (update) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            update = false;
        }
    }
    csrfParam = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrfParam, -1);
    pageContext.setAttribute("csrf", csrfParam);

    if (update) {
        SessionCheckPlugin.XMPP_SESSIONCHECK_INTERVAL.setValue(sessioncheckInterval);
        SessionCheckPlugin.XMPP_SESSIONCHECK_MINTORESTART.setValue(sessioncheckMin);
        SessionCheckPlugin.XMPP_SESSIONCHECK_DEBUGLOGGING.setValue(debuglogEnabled);
        SessionCheckPlugin.XMPP_SESSIONCHECK_RESTARTINTERVAL.setValue(sessioncheckRestartInterval);
        SessionCheckPlugin.XMPP_SESSIONCHECK_ENABLED.setValue(sessioncheckEnabled);
    %>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="sessioncheck.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
    <%
    
    }
    
     if (test) {
        SessionCheckPlugin.runRestartConnnectionListener();
    %>
    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="sessioncheck.settings.test" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
    <%
    
    }

    // Set page vars
    sessioncheckEnabled = SessionCheckPlugin.XMPP_SESSIONCHECK_ENABLED.getValue();
    sessioncheckInterval = SessionCheckPlugin.XMPP_SESSIONCHECK_INTERVAL.getValue();
    sessioncheckMin = SessionCheckPlugin.XMPP_SESSIONCHECK_MINTORESTART.getValue();
    debuglogEnabled = SessionCheckPlugin.XMPP_SESSIONCHECK_DEBUGLOGGING.getValue();
    sessioncheckRestartInterval = SessionCheckPlugin.XMPP_SESSIONCHECK_RESTARTINTERVAL.getValue();

%>


<form action="sessioncheck.jsp">
    <input type="hidden" name="csrf" value="${csrf}">
    <div class="jive-contentBoxHeader">
        <fmt:message key="sessioncheck.settings.title" />
    </div>
    <div class="jive-contentBox">
        <table cellpadding="3" cellspacing="0" border="0">
        <tbody>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="checkbox" name="sessioncheckEnabled" id="sessioncheckEnabled"  <%=(sessioncheckEnabled?"checked" : "")%>>
                </td>
                <td width="99%">
                    <label for="sessioncheckEnabled">
                     <b><fmt:message key="sessioncheck.settings.enable" /></b>
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="checkbox" name="debuglogEnabled" id="debuglogEnabled"  <%=(debuglogEnabled?"checked" : "")%>>
                </td>
                <td width="99%">
                    <label for="debuglogEnabled">
                     <b><fmt:message key="sessioncheck.settings.debuglog" /></b>
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="text" name="sessioncheckInterval" id="sessioncheckInterval"  value="<%=String.valueOf(sessioncheckInterval)%>">
                </td>
                <td width="99%">
                    <label for="sessioncheckInterval">
                     <b><fmt:message key="sessioncheck.settings.interval" /></b>
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="text" name="sessioncheckMin" id="sessioncheckMin"  value="<%=String.valueOf(sessioncheckMin)%>">
                </td>
                <td width="99%">
                    <label for="sessioncheckMin">
                     <b><fmt:message key="sessioncheck.settings.mintorestart" /></b>
                    </label>
                </td>
            </tr>
            <tr valign="top">
                <td width="1%" nowrap>
                    <input type="text" name="sessioncheckRestartInterval" id="sessioncheckRestartInterval"  value="<%=String.valueOf(sessioncheckRestartInterval)%>">
                </td>
                <td width="99%">
                    <label for="sessioncheckRestartInterval">
                     <b><fmt:message key="sessioncheck.settings.restartinterval" /></b>
                    </label>
                </td>
            </tr>
            
        </tbody>
        </table>
    </div>
    <input type="submit" name="update" value="<fmt:message key="global.save_settings" />">
    <input type="submit" name="test" value="<fmt:message key="global.test" />">
</form>
<!-- END 'Set Private Data Policy' -->

</body>
</html>
