package gate.cloud.util;

import java.util.AbstractMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SimpleArrayMap<K, V> extends AbstractMap<K, V> {

  private Set<Entry<K, V>> entries;

  public SimpleArrayMap(K[] keys, V[] values) {
    entries = IntStream.range(0, keys.length).mapToObj((i) -> new SimpleEntry<>(keys[i], values[i]))
            .collect(Collectors.toCollection(CopyOnWriteArraySet::new));
  }

  @Override
  public Set<Entry<K,V>> entrySet() {
    return entries;
  }
}
