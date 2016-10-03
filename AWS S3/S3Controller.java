package com.anetcorp.xblinx.controllers;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;

import org.apache.commons.io.IOUtils;
import org.jets3t.service.model.S3Object;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.anetcorp.xblinx.beans.Response;
import com.anetcorp.xblinx.component.S3Operations;
import com.anetcorp.xblinx.dto.ExpensePreviewDTO;
import com.anetcorp.xblinx.exceptions.SessionExpiredException;
import com.anetcorp.xblinx.utility.Constants;
import com.anetcorp.xblinx.utility.UrlPattern;

/**
 * <b>REST Controller</b> to be used for the operations related to <b>S3</b>.
 * 
 * @author VINOD KUMAR KASHYAP
 * @since 1.0
 */
@RestController
@RequestMapping(UrlPattern.S3_CONTROLLER)
public class S3Controller extends AbstractBaseController {

	@Autowired
	private S3Operations s3Operations;

	@RequestMapping(method = RequestMethod.POST)
	public Response<String> uploadAttachment(MultipartHttpServletRequest request,
			@RequestParam("entity") String entityType, @RequestParam("entity_id") String entityId,
			@RequestParam(value="group_id" , required = false) String groupId, @RequestParam("user_id") String userId)
					throws SessionExpiredException {
		log.debug("in uploadAttachment method");

		Response<String> response = new Response<>();
		S3Object obj = null;
		MultipartFile mpf = null;

		try {
			if ("".equals(entityType) || "".equals(entityId) || "".equals(userId)) {
				response.setStatus(Constants.Response.FAILURE.name());
				response.setMessage("Mandatory parameters missing.(entity/entity_id/user_id)");
			} else {
				Iterator<String> itr = request.getFileNames();
				while (itr.hasNext()) {
					mpf = request.getFile(itr.next());
					obj = s3Operations.uploadAttachment(mpf, entityType, entityId, userId, groupId);
				}
				if (obj != null) {
					response.setStatus(Constants.Response.SUCCESS.name());
					response.setMessage("File " + obj.getName() + " uploaded successfully.");
					log.debug("File " + obj.getName() + " uploaded successfully.");
				}
			}
		} catch (Exception e) {
			response.setStatus(Constants.Response.ERROR.name());
			response.setMessage("File uploaded error.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@RequestMapping(value = UrlPattern.S3_RESUME, method = RequestMethod.POST)
	public Response<String> uploadResume(MultipartHttpServletRequest request,
			@RequestParam("candidate_id") String candidateId) throws SessionExpiredException {
		log.debug("in uploadResume method");

		Response<String> response = new Response<>();
		S3Object obj = null;
		MultipartFile mpf = null;

		try {
			if ("".equals(candidateId)) {
				response.setStatus(Constants.Response.FAILURE.name());
				response.setMessage("Mandatory parameters missing.(candidate_id)");
			} else {
				Iterator<String> itr = request.getFileNames();
				while (itr.hasNext()) {
					mpf = request.getFile(itr.next());
					obj = s3Operations.uploadResume(mpf, candidateId);
				}
				if (obj != null) {
					response.setStatus(Constants.Response.SUCCESS.name());
					response.setMessage("File " + obj.getName() + " uploaded successfully.");
					log.debug("File " + obj.getName() + " uploaded successfully.");
				}
			}
		} catch (Exception e) {
			response.setStatus(Constants.Response.ERROR.name());
			response.setMessage("File uploaded error.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@RequestMapping(value = UrlPattern.S3_RESUME, method = RequestMethod.DELETE)
	public Response<String> deleteResume(HttpServletRequest request, @RequestParam("candidate_id") String candidateId,
			@RequestParam("file_name") String fileName) throws SessionExpiredException {
		log.debug("in deleteResume method");

		Response<String> response = new Response<>();
		try {
			if ("".equals(fileName) || "".equals(candidateId)) {
				response.setStatus(Constants.Response.FAILURE.name());
				response.setMessage("Mandatory parameters missing.(candidate_id/file_name)");
			} else {

				/* delete record from database */
				int count = candidateDao.deleteCandidate(Long.valueOf(candidateId));
				if (count > 0) {
					s3Operations.deleteResume(candidateId, fileName);
					response.setStatus(Constants.Response.SUCCESS.name());
					response.setMessage(fileName + " deleted successfully.");
				} else {
					response.setStatus(Constants.Response.FAILURE.name());
					response.setMessage("No file deleted.");
				}
			}
		} catch (Exception e) {
			response.setStatus(Constants.Response.ERROR.name());
			response.setMessage("Error deleting file: " + fileName + ". Please check all parameter values.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.GET)
	public Response<String> listAttachments(HttpServletRequest request, @RequestParam("entity") String entityType,
			@RequestParam("entity_id") String entityId, @RequestParam("user_id") String userId)
					throws SessionExpiredException {
		log.debug("in listDocuments method");

		Map<String, Object> mapDocs = null;
		Response<String> response = new Response<>();

		try {
			if ("".equals(entityType) || "".equals(entityId) || "".equals(userId)) {
				response.setStatus(Constants.Response.FAILURE.name());
				response.setMessage("Mandatory parameters missing.(entity/entity_id/user_id)");
			} else {
				mapDocs = s3Operations.listDocuments(entityType, entityId, userId);
				response.setStatus(Constants.Response.SUCCESS.name());
				response.setListMap((List<Map<String, String>>) mapDocs.get("items"));
				response.setTotalResults(Integer.parseInt(mapDocs.get("totalCount").toString()));
				response.setEntityId(Long.parseLong(entityId));
				log.debug("Listing douments:" + mapDocs);
			}
		} catch (Exception e) {
			response.setStatus(Constants.Response.ERROR.name());
			response.setMessage("File listing error.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@RequestMapping(value = UrlPattern.S3_DOWNLOAD, method = RequestMethod.GET)
	public Response<String> downloadAttachment(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("entity") String entityType, @RequestParam("entity_id") String entityId,
			@RequestParam("user_id") String userId, @RequestParam(value="group_id" , required = false) String groupId,
			@RequestParam("file_name") String fileName) throws SessionExpiredException {
		log.debug("in downloadDoc method");

		Response<String> res = new Response<>();
		if ("".equals(fileName) || "".equals(entityType) || "".equals(entityId) || "".equals(userId)) {
			res.setStatus(Constants.Response.FAILURE.name());
			res.setMessage("Mandatory parameters missing.(entity/entity_id/user_id/file_name)");
		} else {
			try (InputStream inputStream = s3Operations.downloadDocument(entityType, entityId, userId,
					fileName.replaceAll(" ", "_"), groupId)) {
				response.setContentType("application/force-download; charset=UTF-8");
				response.setCharacterEncoding("UTF-8");
				response.setHeader("Content-Disposition",
						"attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
				IOUtils.copy(inputStream, response.getOutputStream());
				response.flushBuffer();
				log.debug("File downloaded:" + fileName);
			} catch (Exception e) {
				notifyServerError(request, e);
			}
		}
		return res;
	}

	@RequestMapping(value = UrlPattern.S3_PREVIEW, method = RequestMethod.GET)
	public Response<ExpensePreviewDTO> previewAttachment(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("entity") String entityType, @RequestParam("entity_id") String entityId,
			@RequestParam("user_id") String userId, @RequestParam(value="group_id" , required = false) String groupId,
			@RequestParam("file_name") String fileName) throws SessionExpiredException {
		log.debug("in downloadDoc method");

		Response<ExpensePreviewDTO> res = new Response<>();
		try {
			InputStream inputStream = s3Operations.downloadDocument(entityType, entityId, userId,
					fileName.replaceAll(" ", "_"), groupId);
			String extn = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length());
			ExpensePreviewDTO expensePreviewDTO = new ExpensePreviewDTO();
			expensePreviewDTO.setAttachmentName(fileName);
			expensePreviewDTO.setExtension(extn);

			if (Optional.ofNullable(extn).isPresent()
					&& (Constants.RTF_FORMAT.equalsIgnoreCase(extn) )) {
					RTFEditorKit rtfParser = new RTFEditorKit();
					Document doc1 = rtfParser.createDefaultDocument();
					rtfParser.read(inputStream, doc1, 0);
					expensePreviewDTO.setContent(doc1.getText(0, doc1.getLength()));
					expensePreviewDTO.setIsImage(false);
					

			}else if (Optional.ofNullable(extn).isPresent() && Constants.TXT_FORMAT.equalsIgnoreCase(extn)) {

				expensePreviewDTO.setContent(IOUtils.toString(inputStream, "UTF-8"));
				expensePreviewDTO.setIsImage(false);
			} 
			
			else if (Optional.ofNullable(extn).isPresent() && Constants.JPG_FORMAT.equalsIgnoreCase(extn)
					|| Constants.GIF_FORMAT.equalsIgnoreCase(extn) || Constants.JPEG_FORMAT.equalsIgnoreCase(extn)
					|| Constants.PNG_FORMAT.equalsIgnoreCase(extn)) {

				byte[] imageBytes = IOUtils.toByteArray(inputStream);
				String base64 = Base64.getEncoder().encodeToString(imageBytes);
				expensePreviewDTO.setIsImage(true);
				expensePreviewDTO.setContent(base64);
			} 
			else if (Optional.ofNullable(extn).isPresent() && (Constants.PDF_FORMAT.equalsIgnoreCase(extn))){
				response.setContentType("application/pdf; charset=UTF-8");
				response.setCharacterEncoding("UTF-8");
				response.setHeader("Content-Disposition",
						"attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
				IOUtils.copy(inputStream, response.getOutputStream());
				expensePreviewDTO.setIsImage(false);
				response.flushBuffer();	
			}
			
			else {
				response.setContentType("application/force-download; charset=UTF-8");
				response.setCharacterEncoding("UTF-8");
				response.setHeader("Content-Disposition",
						"attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
				IOUtils.copy(inputStream, response.getOutputStream());
				expensePreviewDTO.setIsImage(false);
				response.flushBuffer();

			}
			res.setEntity(expensePreviewDTO);
			log.debug("File downloaded:" + fileName);
		} catch (Exception e) {
			notifyServerError(request, e);
		}
		return res;
	}

	@RequestMapping(value = UrlPattern.S3_RESUMES_DOWNLOAD, method = RequestMethod.GET)
	public Response<String> downloadResume(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("user_id") String userId, @RequestParam("file_name") String docName)
					throws SessionExpiredException {
		log.debug("in downloadResume method");

		Response<String> res = new Response<>();
		if ("".equals(docName) || "".equals(userId)) {
			res.setStatus(Constants.Response.FAILURE.name());
			res.setMessage("Mandatory parameters missing.(user_id/file_name)");
		} else {
			try (InputStream inputStream = s3Operations.downloadResume(userId, docName)) {
				response.setContentType("application/force-download; charset=UTF-8");
				response.setCharacterEncoding("UTF-8");
				response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(docName, "UTF-8"));
				IOUtils.copy(inputStream, response.getOutputStream());
				response.flushBuffer();
				log.debug("File downloaded:" + docName);
			} catch (Exception e) {
				notifyServerError(request, e);
			}
		}
		return res;
	}

	@RequestMapping(method = RequestMethod.DELETE)
	public Response<String> deleteAttachment(HttpServletRequest request, @RequestParam("entity") String entityType,
			@RequestParam("entity_id") String entityId, @RequestParam("user_id") String userId,
			@RequestParam(value="group_id" , required = false) String groupId, @RequestParam("file_name") String fileName)
					throws SessionExpiredException {
		log.debug("in deleteAttachment method");

		Response<String> response = new Response<>();
		try {
			if ("".equals(fileName) || "".equals(entityType) || "".equals(entityId) || "".equals(userId)) {
				response.setStatus(Constants.Response.FAILURE.name());
				response.setMessage("Mandatory parameters missing.(entity/entity_id/user_id/file_name)");
			} else {
				s3Operations.deleteDocument(entityType, entityId, userId, fileName, groupId);
				response.setStatus(Constants.Response.SUCCESS.name());
				response.setMessage(fileName + " deleted successfully.");
			}
		} catch (Exception e) {
			response.setStatus(Constants.Response.ERROR.name());
			response.setMessage("Error deleting file: " + fileName + ". Please check all parameter values.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@RequestMapping(value = "/all", method = RequestMethod.DELETE)
	public Response<String> deleteAllAttachment(HttpServletRequest request, @RequestParam("entity") String entityType,
			@RequestParam("entity_id") String entityId, @RequestParam("user_id") String userId)
					throws SessionExpiredException {
		log.debug("in deleteAllDoc method");

		Response<String> response = new Response<>();
		try {
			if ("".equals(entityType) || "".equals(entityId) || "".equals(userId)) {
				response.setStatus(Constants.Response.FAILURE.name());
				response.setMessage("Mandatory parameters missing.(entity/entity_id/user_id)");
			} else {
				s3Operations.deleteAllDocument(entityType, entityId, userId);
				response.setStatus(Constants.Response.SUCCESS.name());
				response.setMessage("Files deleted successfully.");
			}
		} catch (Exception e) {
			response.setStatus(Constants.Response.ERROR.name());
			response.setMessage("Error deleting files. Please check all parameter values.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}
}
