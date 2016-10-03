package com.s3.operations;

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

/**
 * <b>REST Controller</b> to be used for the operations related to <b>S3</b>.
 * 
 * @author VINOD KUMAR KASHYAP
 * @since 1.0
 */
@RestController
@RequestMapping("/s3")
public class S3Controller extends AbstractBaseController {

	@Autowired
	private S3Operations s3Operations;

	@RequestMapping(method = RequestMethod.POST)
	public Response<String> uploadImage(MultipartHttpServletRequest request,@RequestParam("album_id") String albumId,
			@RequestParam("user_id") String userId)
					throws SessionExpiredException {
		
		Response<String> response = new Response<>();
		S3Object obj = null;
		MultipartFile mpf = null;

		try {
			if ("".equals(albumId) || "".equals(userId)) {
				response.setStatus("FAILURE");
				response.setMessage("Mandatory parameters missing.(album_id/user_id)");
			} else {
				Iterator<String> itr = request.getFileNames();
				while (itr.hasNext()) {
					mpf = request.getFile(itr.next());
					obj = s3Operations.uploadImage(mpf, albumId, userId);
				}
				if (obj != null) {
					response.setStatus("SUCCESS");
					response.setMessage("Image " + obj.getName() + " uploaded successfully.");
					log.debug("Image " + obj.getName() + " uploaded successfully.");
				}
			}
		} catch (Exception e) {
			response.setStatus("ERROR");
			response.setMessage("Image uploaded error.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@SuppressWarnings("unchecked")
	@RequestMapping(method = RequestMethod.GET)
	public Response<String> listImages(HttpServletRequest request,
			@RequestParam("album_id") String albumId, @RequestParam("user_id") String userId)
					throws SessionExpiredException {
		
		Map<String, Object> mapDocs = null;
		Response<String> response = new Response<>();

		try {
			if ("".equals(albumId) || "".equals(userId)) {
				response.setStatus("FAILURE");
				response.setMessage("Mandatory parameters missing.(album_id/user_id)");
			} else {
				mapDocs = s3Operations.listImages(albumId, userId);
				response.setStatus("SUCCESS"());
				response.setListMap((List<Map<String, String>>) mapDocs.get("items"));
				response.setTotalResults(Integer.parseInt(mapDocs.get("totalCount").toString()));
				response.setalbumId(Long.parseLong(albumId));
				log.debug("Listing images:" + mapDocs);
			}
		} catch (Exception e) {
			response.setStatus("ERROR"());
			response.setMessage("Image listing error.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@RequestMapping(value = "/download", method = RequestMethod.GET)
	public Response<String> downloadImage(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("album_id") String albumId,
			@RequestParam("user_id") String userId,
			@RequestParam("file_name") String fileName) throws SessionExpiredException {
		log.debug("in downloadDoc method");

		Response<String> res = new Response<>();
		if ("".equals(fileName) || "".equals(albumId) || "".equals(userId)) {
			res.setStatus("FAILURE");
			res.setMessage("Mandatory parameters missing.(album_id/user_id/file_name)");
		} else {
			try (InputStream inputStream = s3Operations.downloadDocument(albumId, userId,
					fileName.replaceAll(" ", "_"))) {
				response.setContentType("application/force-download; charset=UTF-8");
				response.setCharacterEncoding("UTF-8");
				response.setHeader("Content-Disposition",
						"Image;filename=" + URLEncoder.encode(fileName, "UTF-8"));
				IOUtils.copy(inputStream, response.getOutputStream());
				response.flushBuffer();
				log.debug("Image downloaded:" + fileName);
			} catch (Exception e) {
				notifyServerError(request, e);
			}
		}
		return res;
	}

	@RequestMapping(method = RequestMethod.DELETE)
	public Response<String> deleteImage(HttpServletRequest request, 
			@RequestParam("album_id") String albumId, @RequestParam("user_id") String userId,
			@RequestParam("file_name") String fileName)
					throws SessionExpiredException {
		
		Response<String> response = new Response<>();
		try {
			if ("".equals(fileName) || "".equals(albumId) || "".equals(userId)) {
				response.setStatus("FAILURE");
				response.setMessage("Mandatory parameters missing.(album_id/user_id/file_name)");
			} else {
				s3Operations.deleteDocument(albumId, userId, fileName);
				response.setStatus("SUCCESS");
				response.setMessage(fileName + " deleted successfully.");
			}
		} catch (Exception e) {
			response.setStatus("ERROR");
			response.setMessage("Error deleting file: " + fileName + ". Please check all parameter values.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}

	@RequestMapping(value = "/all", method = RequestMethod.DELETE)
	public Response<String> deleteAllImage(HttpServletRequest request, 
			@RequestParam("album_id") String albumId, @RequestParam("user_id") String userId)
					throws SessionExpiredException {
		
		Response<String> response = new Response<>();
		try {
			if ("".equals(albumId) || "".equals(userId)) {
				response.setStatus("FAILURE");
				response.setMessage("Mandatory parameters missing.(album_id/user_id)");
			} else {
				s3Operations.deleteAllDocument(albumId, userId);
				response.setStatus("SUCCESS");
				response.setMessage("Images deleted successfully.");
			}
		} catch (Exception e) {
			response.setStatus("ERROR");
			response.setMessage("Error deleting images. Please check all parameter values.");
			response.setDevMessage(e.getMessage());
			notifyServerError(request, e);
		}
		return response;
	}
}
