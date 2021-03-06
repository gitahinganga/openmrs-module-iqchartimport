<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<%@ include file="template/localInclude.jsp"%>
<%@ include file="template/localHeader.jsp"%>

<b class="boxHeader"><spring:message code="iqchartimport.upload.uploadNewDatabase" /></b>
<form class="box" method="post" enctype="multipart/form-data" style="margin-bottom: 20px">
	<table>
		<tr>
	    	<td style="font-weight: bold" width="300"><spring:message code="iqchartimport.upload.mdbFile" /></td>
	    	<td>
	    		<input type="file" name="mdbfile" size="40" />
	    	</td>
	    </tr>
	</table>
	<input type="submit" value="<spring:message code="iqchartimport.menu.upload" />" />
	<c:if test="${not empty uploaderror}">
		<span class="error"><c:out value="${uploaderror}" /></span>
	</c:if>
</form>

<c:if test="${database != null}">
	<b class="boxHeader"><spring:message code="iqchartimport.upload.currentDatabase" /></b>
	<form method="post" class="box">
		<table width="100%" cellspacing="0">
			<tr>
				<td style="font-weight: bold" width="300"><spring:message code="iqchartimport.upload.filename" /></td>
				<td><c:out value="${database.name}" /></td>
			</tr>
			<tr>
				<td style="font-weight: bold"><spring:message code="iqchartimport.general.patients" /></td>
				<td>${patientCount}</td>
			</tr>
		</table>
	
		<c:if test="${not empty parseerror}">
			<span class="error"><c:out value="${parseerror}" /></span>
		</c:if>
	</form>
</c:if>

<%@ include file="/WEB-INF/template/footer.jsp"%>