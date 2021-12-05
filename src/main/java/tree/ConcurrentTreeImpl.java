package tree;


import maybe.Maybe;
import maybe.NoMaybeValue;

import java.util.concurrent.locks.ReentrantReadWriteLock;


public class ConcurrentTreeImpl<K extends Comparable<K>, V> extends AbstractConcurrentTree<K, V> {
    public static final boolean debug = false;

    private class TNode {
        K key;
        V value;
        Maybe<TNode> left;
        Maybe<TNode> right;

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

        /**
         * Constructs a TNode given a value which cannot be null
         *
         * @param val a non-null value
         */
        TNode(K key, V val) {
            assert (key != null);
            this.key = key;
            this.value = val;
            this.left = Maybe.none();
            this.right = Maybe.none();
        }
    }

    Maybe<TNode> root;


    public ConcurrentTreeImpl() {
        root = Maybe.none();
    }

    @Override
    public Maybe<V> insert(K key, V val) {
        try {
            return insert(root.get(), key, val);
        } catch (NoMaybeValue nmb) {
            root = Maybe.some(new TNode(key, val));
            return Maybe.none();
        }
    }

    /**
     * insert into a node without doing any rebalancing
     *
     * @param nd  the node to insert in
     * @param key the key to insert at
     * @param val the value to insert
     */
    private Maybe<V> insert(TNode nd, K key, V val) {
        if (key.equals(nd.key)) {
            var tmp = nd.value;
            nd.value = comp.compare(val, tmp) > 0 ? val : tmp;
            return Maybe.from(tmp);
        }
        Maybe<V> ret = Maybe.none();
        if (key.compareTo(nd.key) < 0) {
            try {
                ret = insert(nd.left.get(), key, val);
            } catch (NoMaybeValue nmb) {
                nd.left = Maybe.some(new TNode(key, val));
            }
        } else {
            try {
                ret = insert(nd.right.get(), key, val);
            } catch (NoMaybeValue nmb) {
                nd.right = Maybe.some(new TNode(key, val));
            }
        }
        return ret;
    }

    @Override
    public Maybe<V> get(K key) {
        return get(root, key);
    }


    /**
     * look up the value of {@param key} using {@param nd} as the root of the tree to search through
     *
     * @return none if {@param key} is not in the tree, otherwise return the associated value in a maybe
     */
    private Maybe<V> get(Maybe<TNode> nd, K key) {
        TNode node;
        try {
            node = nd.get();
        } catch (NoMaybeValue nmb) {
            return Maybe.none();
        }
        if (key.compareTo(node.key) < 0)
            return get(node.left, key);
        else if (key.compareTo(node.key) > 0)
            return get(node.right, key);

        return Maybe.from(node.value);
    }

    @Override
    public Maybe<V> give(K key, V val) {
        TNode n = null;
        if (root.equals(Maybe.none())) {
            synchronized (root) {
                try {
                    n = root.get();
                } catch (NoMaybeValue noMaybeValue) {
                    root = Maybe.some(new TNode(key, val));
                    return Maybe.none();
                }
            }
        }
        try {
            n = root.get();
        } catch (NoMaybeValue noMaybeValue) {}
        TNode t;
        while(true){
            t = null;
            n = traversal(n,key); // should write key, val under n
            try{
                n.writeLock.lock();
                if (key.compareTo(n.key) < 0) {
                    try {
                        t = n.left.get();
                        continue; // keep traversing tree, some other thread wrote into its place
                    } catch (NoMaybeValue nmb) {
                        n.left = Maybe.some(new TNode(key, val));
                        return Maybe.none();
                    }
                } else if (key.compareTo(n.key) > 0) {
                    try {
                        t = n.right.get();
                        continue;
                    } catch (NoMaybeValue nmb) {
                        n.right = Maybe.some(new TNode(key, val));
                        return Maybe.none();
                    }
                } else if (key.equals(n.key)) {
                    V tmp = n.value;
                    n.value = comp.compare(val, tmp) > 0 ? val : tmp;
                    return Maybe.from(tmp);
                }
            } finally {
                n.writeLock.unlock();
                if (t!=null) n = t;
            }
        }
    }

    /**
     * Traverses the tree and returns a TNode n such that
     * either key should be inserted as its child or key.equals(n.key).
     * @param n the node to start traversing
     * @param key the key to insert into tree
     * @return leaf node to insert key into
     */
    private TNode traversal(TNode n, K key){
        TNode t;
        n.readLock.lock();
        while (true) {
            t=null;
            try {
                if (key.compareTo(n.key) < 0) {
                    try {
                        t = n.left.get();
                    } catch (NoMaybeValue nmb) {
                        break;
                    }
                } else if (key.compareTo(n.key) > 0) {
                    try {
                        t = n.right.get();
                    } catch (NoMaybeValue nmb) {
                        break;
                    }
                } else if (key.equals(n.key)) {
                    break;
                }
            } finally {
                if (t!=null) t.readLock.lock();
                n.readLock.unlock();
                if (t!=null) n=t;
            }
        }
        return n;
    }

    @Override
    public Maybe<V> query(K key) {
        TNode n = null;
        if (root.equals(Maybe.none())) {
            synchronized (root) {
                try {
                    n = root.get();
                } catch (NoMaybeValue noMaybeValue) {
                    return Maybe.none();
                }
            }
        }
        try {
            n = root.get();
        } catch (NoMaybeValue noMaybeValue) {}
        while(true){
            n = traversal(n,key);
            try{
                n.readLock.lock();
                if (key.equals(n.key)) {
                    return Maybe.from(n.value);
                } else if (n.right.equals(Maybe.none()) && n.left.equals(Maybe.none())){
                    return Maybe.none();
                } else continue;
            } finally{
                n.readLock.unlock();
            }
        }
    }

    public void inOrder() {
        inOrder(root);
        System.out.println();
    }

    private void inOrder(Maybe<TNode> node) {
        TNode nd;
        try {
            nd = node.get();
        } catch (NoMaybeValue nmb) {
            return;
        }
        inOrder(nd.left);
        System.out.print("(" + nd.key + ", " + nd.value + ") ");
        inOrder(nd.right);
    }

    public void preOrder() {
        preOrder(root);
        System.out.println();
    }

    void preOrder(Maybe<TNode> node) {
        TNode nd;
        try {
            nd = node.get();
        } catch (NoMaybeValue nmb) {
            return;
        }
        {
            System.out.print(nd.value + " ");
            preOrder(nd.left);
            preOrder(nd.right);
        }
    }

}
