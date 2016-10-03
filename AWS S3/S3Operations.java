package com.s3.operations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.fileupload.FileUploadBase.IOFileUploadException;
import org.apache.commons.io.IOUtils;
import org.jets3t.service.S3Service;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

@Component
public class S3Operations {

	private S3Service s3Service;
	private S3Bucket s3Bucket;

	/**
	 * Default constructor. This will authenticate the S3 service.
	 * 
	 * @param s3Key
	 *            S3 Bucket Key
	 * @param s3Secret
	 *            S3 Bucket Secret
	 * 
	 * @throws S3ServiceException
	 *             Exception for use by S3Services and related utilities. This
	 *             exception can hold useful additional information about errors
	 *             that occur when communicating with S3.
	 * @throws FileSystemException
	 *             Thrown for file system errors.
	 */
	public S3Operations(String s3Key, String s3Secret) throws S3ServiceException {
		AWSCredentials awsCredentials = new AWSCredentials(s3Key, s3Secret);
		s3Service = new RestS3Service(awsCredentials);
		s3Bucket = s3Service.getBucket(S3_BUCKET);
	}

	/**
	 * This method will upload image to S3 storage. All parameters are
	 * mandatory to be passed for this service to work.
	 * 
	 * @param mpf
	 *            Multipart File to upload.
	 * @param albumId
	 *            Id of the album.
	 * @param userId
	 *            Id of the user.
	 * 
	 * @return S3Object that is uploaded.
	 * @throws S3ServiceException
	 *             Exception for use by S3Services and related utilities. This
	 *             exception can hold useful additional information about errors
	 *             that occur when communicating with S3.
	 * @throws NoSuchAlgorithmException
	 *             This exception is thrown when a particular cryptographic
	 *             algorithm is requested but is not available in the
	 *             environment.
	 * @throws IOException
	 *             Signals that an I/O exception of some sort has occurred. This
	 *             class is the general class of exceptions produced by failed
	 *             or interrupted I/O operations.
	 */
	public S3Object uploadImage(MultipartFile mpf, String albumId, String userId) throws S3ServiceException, IOException {
		InputStream stream = mpf.getInputStream();
		if (s3Bucket == null) {
			s3Bucket = s3Service.getBucket(S3_BUCKET);
		}

		// replacing space with underscore so that file name will not create problem in Linux while downloading
		String fileName = mpf.getOriginalFilename().replaceAll(" ", "_");
		String filePath = userId + "/" + albumId + "/" + fileName;

		S3Object s3Object = new S3Object(filePath);
		s3Object.setDataInputStream(stream);
		s3Object.setContentLength(mpf.getBytes().length);
		s3Object.setContentType(mpf.getContentType());
		S3Object s3Obj = s3Service.putObject(s3Bucket, s3Object);
		stream.close();
		return s3Obj;
	}

	/**
	 * Map of images based on the parameters passed.
	 * 
	 * @param albumId
	 *            Id of the album.
	 * @param userId
	 *            Id of the user.
	 * 
	 * @return Map of images with totalCount, name, lastModified and day of
	 *         upload
	 * @throws S3ServiceException
	 *             Exception for use by S3Services and related utilities. This
	 *             exception can hold useful additional information about errors
	 *             that occur when communicating with S3.
	 */
	public Map<String, Object> listImages(String albumId, String userId)
			throws S3ServiceException {
		Map<String, Object> finalMap = new LinkedHashMap<>();
		List<Map<String, String>> lstDocs = new LinkedList<>();
		long count;

		String prefix = userId + "/" + albumId + "/";
		S3Object[] filteredObjects = s3Service.listObjects(S3_BUCKET, prefix, null);

		count = Arrays.stream(filteredObjects).filter(s3 -> s3.getContentLength() > 1).count();
		Arrays.stream(filteredObjects).filter(s3 -> s3.getContentLength() > 1).forEach(s3 -> {
			Map<String, String> map = new HashMap<>();
			String key = s3.getKey();
			map.put("name", key.substring(key.lastIndexOf("/") + 1, key.length()));
			Date date = s3.getLastModifiedDate();
			LocalDateTime newDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
			map.put("lastModified",
					newDateTime.format(DateTimeFormatter.ofPattern(Constants.DEFAULT_DATE_LONG_FORMAT)));
			map.put(DATETIME, newDateTime.format(DateTimeFormatter.ofPattern(Constants.DATETIME_FORMAT_mm_dd_yyyy)));
			map.put("day", newDateTime.getDayOfWeek().toString());
			lstDocs.add(map);
		});
		finalMap.put("totalCount", Long.toString(count));
		finalMap.put("items", lstDocs.stream().sorted((m1, m2) -> m2.get(DATETIME).compareTo(m1.get(DATETIME)))
				.collect(Collectors.toList()));
		return finalMap;
	}

	/**
	 * Download Image from S3
	 * 
	 * @param albumId
	 *            Id of the album.
	 * @param userId
	 *            Id of the user.
	 * @param imageName
	 *            Name of the image to download
	 * 
	 * @return InputStream for the image to download
	 * @throws ServiceException
	 *             Exception for use by StorageService and related utilities.
	 *             This exception can hold useful additional information about
	 *             errors that occur when communicating with a service.
	 */
	public InputStream downloadImage(String albumId, String userId, String imageName) throws ServiceException {
		String file = userId + "/" + albumId + "/" + imageName;
		S3Object objectComplete = s3Service.getObject(S3_BUCKET, file);
		return objectComplete.getDataInputStream();
	}

	/**
	 * Deletes image from S3.
	 * 
	 * @param albumId
	 *            Id of the album.
	 * @param userId
	 *            Id of the user.
	 * @param imageName
	 *            Name of the image to delete
	 * 
	 * @throws ServiceException
	 *             Exception for use by StorageService and related utilities.
	 *             This exception can hold useful additional information about
	 *             errors that occur when communicating with a service.
	 */
	public void deleteImage(String albumId, String userId, String imageName)
			throws ServiceException {
		imageName = imageName != null && !imageName.isEmpty() ? imageName : "";
		String file = userId + "/" + albumId + "/" + imageName;
		s3Service.deleteObject(S3_BUCKET, file);
	}

	/**
	 * Deletes all images from S3.
	 * 
	 * @param albumId
	 *            Id of the album.
	 * @param userId
	 *            Id of the user.
	 * 
	 * @throws ServiceException
	 *             Exception for use by StorageService and related utilities.
	 *             This exception can hold useful additional information about
	 *             errors that occur when communicating with a service.
	 */
	public void deleteAllImage(String albumId, String userId) throws ServiceException {
		String prefix = userId + "/" + albumId + "/";
		S3Object[] filteredObjects = s3Service.listObjects(S3_BUCKET, prefix, null);
		String[] keys;
		keys = Arrays.stream(filteredObjects).filter(s3 -> s3.getContentLength() > 1).map(s3 -> s3.getKey())
				.toArray(String[]::new);
		if (Optional.ofNullable(keys).isPresent() && keys.length > 0) {
			s3Service.deleteMultipleObjects(S3_BUCKET, Arrays.stream(keys)
					.filter(key -> Optional.ofNullable(key).isPresent() && key.length() > 0).toArray(String[]::new));
		}
	}	
}
