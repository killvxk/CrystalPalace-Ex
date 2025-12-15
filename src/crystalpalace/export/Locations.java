package crystalpalace.export;

import java.util.*;

public class Locations {
	protected Map  primary = new HashMap();
	protected List patches = new LinkedList();

	public Locations() {
	}

	private class Patch {
		protected Object key;
		protected String subkey;
		protected int    location;

		public Patch(Object key, String subkey, int location) {
			this.key      = key;
			this.subkey   = subkey;
			this.location = location;
		}

		public void apply(byte[] data) {
			int value = get(key, subkey);
			crystalpalace.util.CrystalUtils.putDWORD(data, location, value);
		}
	}

	public void fixLater(Object key, String subkey, int location) {
		patches.add(new Patch(key, subkey, location));
	}

	public void put(Object key, String subkey, int value) {
		get(key).put(subkey, value);
	}

	private Map get(Object key) {
		if (primary.get(key) == null)
			primary.put(key, new HashMap());

		return (Map)primary.get(key);
	}

	public int get(Object key, String subkey) {
		if (!get(key).containsKey(subkey))
			throw new RuntimeException("No location for " + key + " -> " + subkey);

		return (int)get(key).get(subkey);
	}

	public void patch(byte[] data) {
		Iterator i = patches.iterator();
		while (i.hasNext()) {
			Patch temp = (Patch)i.next();
			temp.apply(data);
		}
	}
}
