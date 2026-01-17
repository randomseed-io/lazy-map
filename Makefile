SHELL       := /bin/sh
BUILD       := bin/build
DEPLOY      := bin/deploy
DOCS        := bin/docs
UPREADME    := bin/update-readme

VERSION     ?= 1.0.2
GROUP       ?= io.randomseed
APPNAME     ?= lazy-map
DESCRIPTION ?= Lazy maps for Clojure
URL         ?= https://randomseed.io/software/$(APPNAME)/
SCM         ?= github.com/randomseed-io/$(APPNAME)

POMFILE     := pom.xml
JARNAME     := $(APPNAME)-$(VERSION).jar
JARFILE     := target/$(APPNAME)-$(VERSION).jar
DOCPREFIX   := $(GROUP)/$(APPNAME)

.PHONY: default lint docs push-docs
.PHONY: test test-full
.PHONY: sync-pom pom jar
.PHONY: deploy sig tag clean

default: docs

lint:
	bin/lint

readme:
	@echo "[readme]    -> README.md"
	@$(UPREADME) "$(DOCPREFIX)" "$(VERSION)" README.md

docs: readme
	@echo "[docs]      -> docs/"
	@echo "# Introduction" > doc/10_introduction.md
	@tail -n +2 README.md >> doc/10_introduction.md
	@$(DOCS) "$(VERSION)"

push-docs:
	git subtree push --prefix=docs docs main

test:
	@rm -rf .cpcache
	@bin/test

test-full:
	@rm -rf .cpcache
	@bin/test-full

sync-pom:
	@echo "[sync-pom] -> $(POMFILE)"
	@$(BUILD) sync-pom :group "\"$(GROUP)\"" :name "\"$(APPNAME)\"" :version "\"$(VERSION)\"" :description "\"$(DESCRIPTION)\"" :scm "\"$(SCM)\"" :url "\"$(URL)\""

pom: clean
	@echo "[pom]      -> $(POMFILE)"
	@rm -f $(POMFILE).asc
	@$(MAKE) -s sync-pom

jar: pom
	@echo "[jar]      -> $(JARNAME)"
	@rm -rf target/classes || true
	@rm -f $(JARFILE) || true
	@$(BUILD) jar :group "\"$(GROUP)\"" :name "\"$(APPNAME)\"" :version "\"$(VERSION)\""

sig:
	@echo "[sig]      -> $(POMFILE).asc"
	@rm -f "$(POMFILE).asc" || true
	@gpg2 --armor --detach-sig "$(POMFILE)"

deploy: clean pom jar
	@echo "[deploy]   -> $(GROUP)/$(APPNAME)-$(VERSION)"
	@test -f "$(JARFILE)" || (echo "Missing $(JARFILE)"; exit 1)
	@test -f "pom.xml" || (echo "Missing pom.xml"; exit 1)
	@$(DEPLOY) deploy :artifact "\"$(JARFILE)\""
	@test -f "$(APPNAME)-$(VERSION).pom.asc" && mv -f "$(APPNAME)-$(VERSION).pom.asc" "$(POMFILE).asc" || true

tag:
	git tag -s "$(VERSION)" -m "Release $(VERSION)"

clean:
	@rm -f target/*.jar "$(POMFILE).asc" || true
	@find . -name .DS_Store -print0 | xargs -0 rm -f
