package org.smartcolors.marshal;

import com.google.common.base.Throwables;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by devrandom on 2014-Nov-17.
 */
public class HashSerializer extends BytesSerializer {
	@Override
	public void write(Serializable obj) throws SerializationException {
		write(obj.getHash());
	}

	public static byte[] calcHash(BytesSerializer serializer, byte[] hmacKey) {
		Mac hmac = null;
		try {
			hmac = Mac.getInstance("HmacSHA256");
		} catch (NoSuchAlgorithmException e) {
			Throwables.propagate(e);
		}
		SecretKeySpec macKey = new SecretKeySpec(hmacKey, "RAW");
		try {
			hmac.init(macKey);
		} catch (InvalidKeyException e) {
			Throwables.propagate(e);
		}
		return hmac.doFinal(serializer.getBytes());
	}

	public static byte[] calcHash(byte[] content, byte[] hmacKey) {
		Mac hmac = null;
		try {
			hmac = Mac.getInstance("HmacSHA256");
		} catch (NoSuchAlgorithmException e) {
			Throwables.propagate(e);
		}
		SecretKeySpec macKey = new SecretKeySpec(hmacKey, "RAW");
		try {
			hmac.init(macKey);
		} catch (InvalidKeyException e) {
			Throwables.propagate(e);
		}
		return hmac.doFinal(content);
	}
}
