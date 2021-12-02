package tree;

import maybe.Maybe;

import java.util.Comparator;

public interface ConcurrentTree<K extends Comparable<K>, V>
{

    /**
     * insert without doing any rebalancing
     * if {@param key} is already mapped, then map it to the max of its old mapping
     * and {@param val}, as ordered by the current comparator
     * return the previous mapping of {@param key} if there was one
     */
    Maybe<V> insert(K key, V val);


    /**
     * thread safe insert
     */
    Maybe<V> give(K key, V val);

    /**
     * single threaded lookup
     * @return the mapping of {@param key} if there is one
     */
    Maybe<V> get(K key);

    /**
     * thread safe version of get
     */
    Maybe<V> query(K key);


    /**
     * print preorder traversal of this
     */
    void preOrder();


    /**
     * print inorder traversal of this
     */
    void inOrder();

    /**
     * Set the value comparator to {@param c}
     */
    void setCompare(Comparator<V> c);

}
