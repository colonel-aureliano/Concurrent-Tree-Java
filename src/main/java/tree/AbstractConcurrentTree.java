package tree;


import java.util.Comparator;

/**
 * Abstract class of ConcurrentTree. Provides implementation of setting comparator
 */
public abstract class AbstractConcurrentTree<K extends Comparable<K>, V> implements ConcurrentTree<K, V>
  {
    protected Comparator<V> comp = Comparator.comparing(Object::toString);

    @Override
    public void setCompare(Comparator<V> comp)
      {
        this.comp = comp;
      }
  }
