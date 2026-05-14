from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from threading import Lock


def _normalize_labels(labels: dict[str, str | int | bool | None]) -> tuple[tuple[str, str], ...]:
    normalized = []
    for key, value in labels.items():
        normalized.append((str(key), "" if value is None else str(value)))
    normalized.sort(key=lambda item: item[0])
    return tuple(normalized)


@dataclass(slots=True)
class CounterSample:
    value: int = 0


@dataclass(slots=True)
class DistributionSample:
    count: int = 0
    sum: float = 0.0
    max: float = 0.0

    def observe(self, value: float) -> None:
        self.count += 1
        self.sum += value
        self.max = max(self.max, value)

    @property
    def avg(self) -> float:
        if self.count == 0:
            return 0.0
        return round(self.sum / self.count, 2)


class MetricsRegistry:
    def __init__(self) -> None:
        self._lock = Lock()
        self._counters: dict[tuple[str, tuple[tuple[str, str], ...]], CounterSample] = defaultdict(
            CounterSample
        )
        self._distributions: dict[
            tuple[str, tuple[tuple[str, str], ...]], DistributionSample
        ] = defaultdict(DistributionSample)

    def increment(self, name: str, amount: int = 1, **labels: str | int | bool | None) -> None:
        key = (name, _normalize_labels(labels))
        with self._lock:
            self._counters[key].value += amount

    def observe(self, name: str, value: float, **labels: str | int | bool | None) -> None:
        key = (name, _normalize_labels(labels))
        with self._lock:
            self._distributions[key].observe(float(value))

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            counters = [
                {
                    "name": name,
                    "labels": dict(label_items),
                    "value": sample.value,
                }
                for (name, label_items), sample in sorted(self._counters.items(), key=lambda item: item[0][0])
            ]
            distributions = [
                {
                    "name": name,
                    "labels": dict(label_items),
                    "count": sample.count,
                    "sum": round(sample.sum, 2),
                    "avg": sample.avg,
                    "max": round(sample.max, 2),
                }
                for (name, label_items), sample in sorted(
                    self._distributions.items(), key=lambda item: item[0][0]
                )
            ]

        return {
            "counters": counters,
            "distributions": distributions,
        }
