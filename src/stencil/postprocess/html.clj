(ns stencil.postprocess.html
  "Replaces results of html() calls with external part relationships."
  (:require [stencil.parts :refer [register-external!]]
            [stencil.util :refer :all]))

(def ooxml-p :xmlns.http%3A%2F%2Fschemas.openxmlformats.org%2Fwordprocessingml%2F2006%2Fmain/p)

(deftype HtmlChunk [content])

(defmethod call-fn "html" [_ content] (->HtmlChunk content))

(defn ->html-chunk [id]
  {:tag :xmlns.http%3A%2F%2Fschemas.openxmlformats.org%2Fwordprocessingml%2F2006%2Fmain/val/altChunk
   :attrs {:xmlns.http%3A%2F%2Fschemas.openxmlformats.org%2Fpackage%2F2006%2Frelationships/id id}
   :content []})

(defn- find-enclosing-p [loc]
  (find-first (comp #{ooxml-p} :tag) (take-while some? (iterations zip/up loc))))

(defn- register-html! [id content]
  (register-external!
   :id id
   :file-name (str id ".html")
   :rel-type "http://schemas.openxmlformats.org/officeDocument/2006/relationships/aFChunk"
   :rel-target-mode "Internal"
   :content-type "text/html"))

(defn- fix-html-chunk [chunk]
  (let [id (str (java.util.UUID/randomUuid))]
    (if-let [chunk-p-parent (find-enclosing-p)]
      ;; then finds its <p> parent and replace it with a ->html-chunk call
      (do (register-html! id (:content chunk))
          (zip/replace chunk-p-parent (->html-chunk id)))
      tree)))

(defn fix-html-chunks [xml-tree]
  (dfs-fix-xml-node xml-tree #(instance? HtmlChunk %) fix-html-chunk))
