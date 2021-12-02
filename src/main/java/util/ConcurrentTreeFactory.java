package util;

import tree.*;

/**
 * Change this file to change what gets run in the tests
 */
public class ConcurrentTreeFactory
  {
    public static ConcurrentTree<String, String> makeStringTree()
      {
        return new ConcurrentTreeImpl<>();
      }
    public static ConcurrentTree<Integer, Integer> makeIntTree()
      {
        return new ConcurrentTreeImpl<>();
      }
  }
