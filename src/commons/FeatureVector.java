package commons;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class FeatureVector implements Serializable
{	
	private static final long serialVersionUID = 9029197104497329900L;

	// different from Mallet, just use a JDK HashMap to restore the sparse vector
	HashMap<Object, Double> map;
	
	public Map<Object, Double> getMap()
	{
		return map;
	}
	
	public FeatureVector (Object[] feats, double[] values, int capacity) 
	{
		this(capacity);
		for(int key=0; key<feats.length; key++)
		{
			double value = values[key];
			Object feature = feats[key];
			map.put(feature, value);
		}
	}
	
	public FeatureVector ()
	{
		this(50);
	}
	
	public FeatureVector (int capacity) 
	{
		map = new HashMap<Object, Double>(capacity);
	}

	public Double get(Object key)
	{
		return this.map.get(key);
	}
	
	/**
	 * Qi: add a value into the feature vector
	 * different from add, use += if value exists already
	 * @param index
	 * @param value
	 */
	public void add(Object feat, double value)
	{
		// first, check if index already exists
		Double value_exist = map.get(feat);
		if(value_exist == null)
		{
			map.put(feat, value);
		}
		else
		{
			value_exist += value;
			map.put(feat, value_exist);
		}
	}

	public FeatureVector clone()
	{
		FeatureVector fv = new FeatureVector();
		fv.map = new HashMap<Object, Double>(this.map);
		return fv;
	}

	public final double dotProduct (FeatureVector fv) 
	{
		double ret = 0.0;
		Map<Object, Double> map1 = map;
		Map<Object, Double> map2 = fv.map;
		if(map2.size() < map1.size())
		{
			map1 = fv.map;
			map2 = map;
		}
		for(Entry<Object, Double> entry : map1.entrySet())
		{
			Double value2 = map2.get(entry.getKey());
			if(value2 != null)
			{
				ret += entry.getValue() * value2;
			}
		}
		return ret;
	}

	/**
	 * this = this + (fv1 - fv2) * factor
	 * this function is for updating parameters in perceptron
	 * @param fv1
	 * @param fv2
	 * @param factor
	 */
	public void addDelta(FeatureVector fv1, FeatureVector fv2, double factor)
	{
		for(Object key : fv1.map.keySet())
		{
			Double value1 = fv1.get(key);
			Double value2 = fv2.get(key);
			if(value2 == null)
			{
				value2 = 0.0;
			}
			double value = (value1 - value2) * factor;
			if(value != 0.0)
			{
				this.add(key, value);
			}
		}
		for(Object key : fv2.map.keySet())
		{
			Double value1 = fv1.get(key);
			Double value2 = fv2.get(key);
			if(value1 == null)
			{
				double value = (0.0 - value2) * factor;
				if(value != 0.0)
				{
					this.add(key, value);
				}
			}
		}
	}
	
	// add indices in v if they are not in this, and then plusEquals(v, factor) 
	public void plusEquals (FeatureVector v) 
	{
		plusEquals(v, 1.0);
	}
	
	// add indices in v if they are not in this, and then plusEquals(v, factor) 
	public void plusEquals (FeatureVector fv, double factor) 
	{
		for(Object key : fv.map.keySet())
		{
			Double value_new = fv.map.get(key) * factor; 
			Double value = map.get(key);
			if(value == null)
			{
				map.put(key, value_new);
			}
			else
			{
				map.put(key, value + value_new);
			}
		}
	}
	
	public void multiply(double factor)
	{
		for(Object key : map.keySet())
		{
			Double value = map.get(key);
			map.put(key, value * factor);
		}
	}
	
	public String toString()
	{
		return toString(false);
	}
	
	public String toString (FeatureVector weights)
	{
		//Thread.currentThread().dumpStack();
		StringBuffer sb = new StringBuffer ();
		
	    for(Object key : map.keySet()) 
	    {
			Double value = map.get(key);
			sb.append (key);
			sb.append ("=");
			sb.append (value);
			sb.append (' ');
			sb.append(weights.get(key));	// weight
			sb.append ('\n');
	    }
		return sb.toString();
	}
	
	public String toString (boolean onOneLine)
	{
		//Thread.currentThread().dumpStack();
		StringBuffer sb = new StringBuffer ();
		
	    for(Object key : map.keySet()) 
	    {
			Double value = map.get(key);
			sb.append (key);
			sb.append ("=");
			sb.append (value);
			if (!onOneLine)
			    sb.append ("\n");
			else
			    sb.append (' ');
	    }
		return sb.toString();
	}

	public int size()
	{
		return map.size();
	}
	
	public static void main(String[] args)
	{
		// test map operations
		HashMap<Integer, Double> map = new HashMap<Integer, Double>();
		map.put(1, 1.0);
		map.put(2, 2.0);
		map.put(3, 3.0);
		
		HashMap<Integer, Double> map2 = (HashMap<Integer, Double>) map.clone();
		map.put(3, 4.0);
		for(Integer key : map2.keySet())
		{
			Double value = map2.get(key);
			System.out.println(key + " " + value);
		}
		
		Double temp = map2.get(3);
		temp++;
		for(Integer key : map2.keySet())
		{
			Double value = map2.get(key);
			System.out.println(key + " " + value);
		}
	}
}
