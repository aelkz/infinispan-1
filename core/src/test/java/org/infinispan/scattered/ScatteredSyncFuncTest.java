package org.infinispan.scattered;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import static org.testng.AssertJUnit.fail;

import org.infinispan.commons.CacheException;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DistSyncFuncTest;
import org.infinispan.distribution.MagicKey;
import org.testng.annotations.Test;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Test(groups = "functional")
public class ScatteredSyncFuncTest extends DistSyncFuncTest {
   public ScatteredSyncFuncTest() {
      cacheMode = CacheMode.SCATTERED_SYNC;
      numOwners = 1;
      l1CacheEnabled = false;
   }

   @Override
   protected void assertOwnershipAndNonOwnership(Object key, boolean allowL1) {
      Utils.assertOwnershipAndNonOwnership(caches, key);
   }

   public void testCompute() {
      MagicKey key = new MagicKey(cache(0, cacheName));
      cache(1, cacheName).put(key, "a");
      // from non-owner and non-last writer
      assertEquals("ab", cache(2, cacheName).compute(key, (k, v) -> v + "b"));
      assertLocalValue(0, key, "ab");
      assertLocalValue(1, key, "a");
      assertLocalValue(2, key, "ab");
      assertOwnershipAndNonOwnership(key, false);

      // from owner
      assertEquals("abc", cache(0, cacheName).compute(key, (k, v) -> v + "c"));
      assertLocalValue(0, key, "abc");
      // we don't know which node become backup
      assertOwnershipAndNonOwnership(key, false);

      // removing from non-owner
      assertEquals(null, cache(1, cacheName).compute(key, (k, v) -> "abc".equals(v) ? null : "unexpected"));
      assertLocalValue(0, key, null);
      assertLocalValue(1, key, null);
      assertLocalValue(2, key, "ab");
      assertOwnershipAndNonOwnership(key, false);

      MagicKey otherKey = new MagicKey(cache(0, cacheName));

      // on non-existent value, non-owner
      assertEquals("x", cache(1, cacheName).compute(otherKey, (k, v) -> v == null ? "x" : "unexpected"));
      assertLocalValue(0, otherKey, "x");
      assertLocalValue(1, otherKey, "x");
      assertNoLocalValue(2, otherKey);
      assertOwnershipAndNonOwnership(otherKey, false);

      // removing from owner
      assertEquals(null, cache(0, cacheName).compute(otherKey, (k, v) -> "x".equals(v) ? null : "unexpected"));
      assertLocalValue(0, otherKey, null);
      assertLocalValue(1, otherKey, null);
      assertNoLocalValue(2, otherKey);
      assertOwnershipAndNonOwnership(otherKey, false);

      // on tombstone, from owner
      assertEquals("y", cache(0, cacheName).compute(otherKey, (k, v) -> v == null ? "y" : "unexpected"));
      assertLocalValue(0, otherKey, "y");
      assertOwnershipAndNonOwnership(otherKey, false);
   }

   public void testComputeIfPresent() {
      MagicKey key = new MagicKey(cache(0, cacheName));
      cache(1, cacheName).put(key, "a");

      // on non-owner and non-last-writer
      assertEquals("ab", cache(2, cacheName).computeIfPresent(key, (k, v) -> v + "b"));
      assertLocalValue(0, key, "ab");
      assertLocalValue(1, key, "a");
      assertLocalValue(2, key, "ab");
      assertOwnershipAndNonOwnership(key, false);

      // from owner
      assertEquals("abc", cache(0, cacheName).computeIfPresent(key, (k, v) -> v + "c"));
      assertLocalValue(0, key, "abc");
      // we don't know which node become backup
      assertOwnershipAndNonOwnership(key, false);

      // removing from non-owner
      assertEquals(null, cache(1, cacheName).computeIfPresent(key, (k, v) -> "abc".equals(v) ? null : "unexpected"));
      assertLocalValue(0, key, null);
      assertLocalValue(1, key, null);
      assertLocalValue(2, key, "ab");
      assertOwnershipAndNonOwnership(key, false);

      // on tombstone from owner
      assertEquals(null, cache(0, cacheName).computeIfPresent(key, (k, v) -> "unexpected"));
      assertLocalValue(0, key, null);
      assertLocalValue(1, key, null);
      assertLocalValue(2, key, "ab");
      assertOwnershipAndNonOwnership(key, false);

      MagicKey otherKey = new MagicKey(cache(0, cacheName));

      // on non-existent value, non-owner
      assertEquals(null, cache(1, cacheName).computeIfPresent(otherKey, (k, v) -> "unexpected"));
      assertNoLocalValue(0, otherKey);
      assertNoLocalValue(1, otherKey);
      assertNoLocalValue(2, otherKey);
      // cannot use assertOwnershipAndNonOwnership: no entry should be in any of the caches
   }

   public void testComputeIfAbsent() {
      MagicKey key = new MagicKey(cache(0, cacheName));
      cache(1, cacheName).put(key, "a");

      // from non-owner, non-last writer
      cache(2, cacheName).computeIfAbsent(key, k -> "b");
      assertLocalValue(0, key, "a");
      assertLocalValue(1, key, "a");
      assertNoLocalValue(2, key);
      assertEquals("a", cache(2, cacheName).get(key));
      assertOwnershipAndNonOwnership(key, false);

      // from non-owner, last writer
      cache(1, cacheName).computeIfAbsent(key, k -> "c");
      assertLocalValue(0, key, "a");
      assertLocalValue(1, key, "a");
      assertNoLocalValue(2, key);
      assertEquals("a", cache(2, cacheName).get(key));
      assertOwnershipAndNonOwnership(key, false);

      // from owner
      cache(2, cacheName).computeIfAbsent(key, k -> "d");
      assertLocalValue(0, key, "a");
      assertLocalValue(1, key, "a");
      assertNoLocalValue(2, key);
      assertEquals("a", cache(2, cacheName).get(key));
      assertOwnershipAndNonOwnership(key, false);

      MagicKey otherKey = new MagicKey(cache(0, cacheName));

      // on non-existent value from non-owner
      assertEquals("x", cache(1, cacheName).computeIfAbsent(otherKey, k -> "x"));
      assertLocalValue(0, otherKey, "x");
      assertLocalValue(1, otherKey, "x");
      assertNoLocalValue(2, otherKey);
      assertOwnershipAndNonOwnership(key, false);

      // on existent value from non-owner, non-last-writer
      assertEquals("x", cache(2, cacheName).computeIfAbsent(otherKey, k -> "y"));
      assertLocalValue(0, otherKey, "x");
      assertLocalValue(1, otherKey, "x");
      assertNoLocalValue(2, otherKey);
      assertOwnershipAndNonOwnership(key, false);

      // removal on owner should do nothing
      assertEquals("x", cache(0, cacheName).computeIfAbsent(otherKey, k -> null));
      assertLocalValue(0, otherKey, "x");
      assertLocalValue(1, otherKey, "x");
      assertNoLocalValue(2, otherKey);
      assertOwnershipAndNonOwnership(key, false);

      // removal on non-owner should do nothing
      assertEquals("x", cache(1, cacheName).computeIfAbsent(otherKey, k -> null));
      assertLocalValue(0, otherKey, "x");
      assertLocalValue(1, otherKey, "x");
      assertNoLocalValue(2, otherKey);
      assertOwnershipAndNonOwnership(key, false);

      // removal on non-owner, non-last-writer should do nothing
      assertEquals("x", cache(2, cacheName).computeIfAbsent(otherKey, k -> null));
      assertLocalValue(0, otherKey, "x");
      assertLocalValue(1, otherKey, "x");
      assertNoLocalValue(2, otherKey);
      assertOwnershipAndNonOwnership(key, false);

   }

   protected void assertNoLocalValue(int node, MagicKey key) {
      InternalCacheEntry<Object, Object> ice = cache(node, cacheName).getAdvancedCache().getDataContainer().get(key);
      assertEquals(null, ice);
   }

   protected void assertLocalValue(int node, MagicKey key, String expectedValue) {
      InternalCacheEntry<Object, Object> ice = cache(node, cacheName).getAdvancedCache().getDataContainer().get(key);
      assertNotNull(ice);
      assertEquals(expectedValue, ice.getValue());
   }

   @Override
   public void testMergeFromNonOwner() {
      // TODO : Add support for ScatteredCaches in functional commands : https://issues.jboss.org/browse/ISPN-8078
      RuntimeException mergeException = new RuntimeException("hi there");

      try {
         getFirstNonOwner("k1").merge("k1", "ex", (k, v) -> {
            throw mergeException;
         });
         fail("Exception was not thrown");
      } catch (CacheException ex) {
         assertEquals(UnsupportedOperationException.class, ex.getCause().getClass());
      }
   }
}
