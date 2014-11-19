package org.smartcolors.marshal;

import com.google.common.base.Throwables;
import com.google.common.hash.HashCode;

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
		write(obj.getHash().asBytes());
	}

	@Override
	public void write(Object obj, SerializerHelper helper) throws SerializationException {
		write(helper.getHash(obj).asBytes());
	}

	public static HashCode calcHash(BytesSerializer serializer, byte[] hmacKey) {
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
		return HashCode.fromBytes(hmac.doFinal(serializer.getBytes()));
	}

	public static HashCode calcHash(byte[] content, byte[] hmacKey) {
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
		return HashCode.fromBytes(hmac.doFinal(content));
	}
}
