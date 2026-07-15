(ns cellworks.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/ship-cell-batch`/`:actuation/issue-safety-
  certificate` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [cellworks.phase :as phase]))

(deftest ship-cell-batch-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real robot cell-batch shipment"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/ship-cell-batch))
          (str "phase " n " must not auto-commit :actuation/ship-cell-batch")))))

(deftest issue-safety-certificate-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real Battery Safety Test Report"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/issue-safety-certificate))
          (str "phase " n " must not auto-commit :actuation/issue-safety-certificate")))))

(deftest end-of-line-quality-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :end-of-line-quality/screen))
          (str "phase " n " must not auto-commit :end-of-line-quality/screen")))))

(deftest robotics-simulate-crush-test-never-auto-at-any-phase
  (testing "the robot UN 38.3 T6 crush-test verification mission carries no direct capital risk, but is still never auto-eligible, matching every sibling verification op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :robotics/simulate-crush-test))
          (str "phase " n " must not auto-commit :robotics/simulate-crush-test")))))

(deftest robotics-simulate-crush-test-enabled-from-phase-2
  (is (contains? (:writes (get phase/phases 2)) :robotics/simulate-crush-test))
  (is (contains? (:writes (get phase/phases 3)) :robotics/simulate-crush-test))
  (is (not (contains? (:writes (get phase/phases 1)) :robotics/simulate-crush-test))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":cell-batch/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:cell-batch/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :cell-batch/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/ship-cell-batch} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/issue-safety-certificate} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :cell-batch/intake} :commit)))))
