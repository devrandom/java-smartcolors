package org.smartcolors.marshal;

import com.google.common.collect.Lists;

import java.io.InputStream;
import java.util.List;

/**
 * Created by devrandom on 2014-Nov-19.
 */
public class MemoizedDeserializer extends StreamDeserializer {
	List<Object> memos = Lists.newArrayList();
	public MemoizedDeserializer(InputStream is) {
		super(is);
	}

	// FIXME use this for reading sub-objects?
	public <T> T readObject(ObjectReader<T> reader) throws SerializationException {
		long idx = readVaruint();
		if (idx > 0) {
			if (idx - 1 >= memos.size())
				throw new SerializationException("invalid index " + idx + " only have " + memos.size());
			return (T)memos.get((int)idx - 1);
		} else {
			T obj = reader.readObject(this);
			memos.add(obj);
			return obj;
		}
	}
}
