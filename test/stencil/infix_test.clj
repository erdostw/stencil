(ns stencil.infix-test
  (:import [clojure.lang ExceptionInfo])
  (:require [stencil.infix :as infix :refer :all]
            [stencil.types :refer [hide-table-column-marker?]]
            [clojure.test :refer [deftest testing is are]]))

(defn- run [xs] (infix/eval-rpn {} (infix/parse xs)))

(deftest tokenize-test
  (testing "simple fn call"
    (is (= [(->FnCall "sin") 1 :plus 2 :close] (infix/tokenize "   sin(1+2)"))))

  (testing "comma"
    (is (= [:open 1 :comma 2 :comma 3 :close] (infix/tokenize "  (1,2   ,3)   ")))))

(deftest tokenize-string-literal
  (testing "spaces are kept"
    (is (= [" "] (infix/tokenize " \" \" ")))
    (is (= [" x "] (infix/tokenize " \" x \" "))))
  (testing "escape characters are supported"
    (is (= ["aaa\"bbb"] (infix/tokenize "\"aaa\\\"bbb\"")))))

(deftest tokenize-string-fun-eq
  (testing "tricky"
    (is (=  ["1" :eq #stencil.infix.FnCall{:fn-name "str"} 1 :close]
            (infix/tokenize "\"1\" = str(1)")))
    (is (= ["1" 1 {:fn "str" :args 1} :eq]
           (infix/parse "\"1\" = str(1)")))))

(deftest parse-simple
  (testing "Empty"
    (is (thrown? ExceptionInfo (infix/parse nil)))
    (is (thrown? ExceptionInfo (infix/parse ""))))

  (testing "Simple values"
    (is (= [12] (infix/parse "  12 ") (infix/parse "12")))
    (is (= '[ax.y] (infix/parse "   ax.y  "))))

  (testing "Simple operations"
    (is (= [1 2 :plus]
           (infix/parse "1 + 2")
           (infix/parse "1+2    ")
           (infix/parse "1+2")))
    (is (= [3 2 :times] (infix/parse "3*2"))))

  (testing "Parentheses"
    (is (= [3 2 :plus 4 :times]
           (infix/parse "(3+2)*4")))
    (is (= [3 2 :plus 4 1 :minus :times]
           (infix/parse "(3+2)*(4 - 1)")))))

(deftest all-ops-supported
  (testing "Minden operatort vegre tudunk hajtani?"
    (let [ops (-> #{}
                  (into (vals infix/ops))
                  (into (vals infix/ops2))
                  (into (keys infix/operation-tokens))
                  (disj :open :close :comma))
          known-ops (set (filter keyword? (keys (methods @#'infix/reduce-step))))]
      (is (every? known-ops ops)))))

(deftest basic-arithmetic
  (testing "Alap matek"

    (testing "Egyszeru ertekek"
      (is (= 12 (run " 12  "))))

    (testing "Aritmetika"
      (is (= 5 (run "2 +3")))
      (is (= 3 (run "5 - 2")))
      (is (= 6 (run "2 * 3")))
      (is (= 3 (run "6/2"))))

    (testing "Simple op precedence"
      (is (= 7 (run "1+2*3")))
      (is (= 7 (run "2*3+1")))
      (is (= 9 (run "(1+2)*3")))
      (is (= 9 (run "3*(1+2)"))))

    (testing "Osszehasonlito muveletek"
      (is (true? (run "3 = 3")))
      (is (false? (run "3 == 4")))
      (is (true? (run "3 != 4")))
      (is (false? (run "34 != 34"))))

    (testing "Osszehasonlito muveletek - 2"
      (is (true? (run "3 < 4")))
      (is (false? (run "4 < 2")))
      (is (true? (run "3 <= 3")))
      (is (true? (run "34 >= 2"))))

    (testing "Logikai muveletek"
      (is (true? (run "3 = 3 && 4 == 4"))))

    :ok))

(deftest operator-precedeces
  (testing "Operator precedencia"
    (is (= 36 (run "2*3+5*6"))))

  (testing "Operator precedencia - tobb tagu"
    (is (= 36 (run "2*(1+1+1) + (7 - 2) * 6")))
    (is (= 16 (run "6 + (5)*2")))
    (is (= 22 (run "2*( 1+5) + (5)*2")))
    (is (= 7 (run "(2-1)*2+(7-2)"))))

  (testing "Advanced operator precedences"
    ;; https://gist.github.com/PetrGlad/1175640#gistcomment-876889
    (is (= 21 (run "1+((2+3)*4)"))))

  :ok)

(deftest negativ-szamok
  (is (= -123 (run " -123")))
  (is (= -6 (run "-3*2")))
  (is (= -6 (run "2*-3")))
  (is (= -6 (run "2*(-3)")))
  (testing "a minusz jel precedenciaja nagyon magas"
    (is (= 20 (run "10/-1*-2 ")))))

(deftest range-function
  (testing "Wrong arity calls"
    ;; fontos, hogy nem csak tovabbhivunk a range fuggvenyre,
    ;; mert az vegtelen szekvenciat eredmenyezne.
    (is (thrown? ExceptionInfo (run "range()")))
    (is (thrown? ExceptionInfo (run "range(1,2,3,4)"))))

  (testing "fn to create a number range"
    (is (= [0 1 2 3 4] (run "range(5)")))
    (is (= [1 2 3 4] (run "range (1,5)")))
    (is (= [1 3 5] (run "range( 1, 6, 2)")))))

(deftest length-function
  (testing "Simple cases"
    (is (= 2 (run "length(\"ab\")"))))
  (testing "Simple cases"
    (is (= true (run "length(\"\")==0")))
    (is (= true (run "1 = length(\" \")")))))

(deftest test-colhide-expr
  (is (hide-table-column-marker? (run "hideColumn()"))))

(deftest test-unexpected
  (is (thrown? ExceptionInfo (parse "aaaa:bbbb"))))

(deftest tokenize-wrong-tokens
  (testing "Misplaced operators and operands"
    (are [x] (thrown? ExceptionInfo (infix/parse x))
      "1++2" "1 2" "1 2 3" "--" "1+" "+ 1" "+-2" "2 3 *" "+*" "/*" "*1*"))
  (testing "Misplaced parentheses"
    (are [x] (thrown? ExceptionInfo (infix/parse x))
      "2)(3" "(23)3" "1(2)" "(2)(3)" ")2" ")" "(" ")(" ")()" "(())" "()" "()()" "1+(1" "(2"))
  (testing "Illegal coma usage"
    (are [x] (thrown? ExceptionInfo (infix/parse x))
      "2,3,4" ",,,,2" ",3" "3," "+,-"))
  (testing "syntax errors should be thrown"
    (are [x] (thrown? ExceptionInfo (infix/parse x))
      "* 1 + 2 4"))
  (testing "syntax errors should be thrown"
    (are [x] (thrown? ExceptionInfo (infix/parse x))
      "* 3 4" "- 1 a" "+ x 2" "/ x y")))

:ok
