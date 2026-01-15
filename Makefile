SHELL   := /bin/sh
BUILD   := bin/build
VERSION := $(shell awk 'NF{print $$1; exit}' VERSION)

APPNAME ?= lazy-map

POMFILE := pom.xml
JARFILE := target/$(APPNAME)-$(VERSION).jar

.PHONY: default lint docs push-docs \
        test test-full \
        sync-pom pom jar \
        deploy sig tag clean

default: docs

lint:
	bin/lint

docs:
	echo "# Introduction" > doc/10_introduction.md
	tail -n +2 README.md >> doc/10_introduction.md
	bin/docs "$(VERSION)"

push-docs:
	git subtree push --prefix=docs docs main

test:
	@rm -rf .cpcache
	@bin/test

test-full:
	@rm -rf .cpcache
	@bin/test-full

sync-pom:
	@echo "[sync-pom]"
	$(BUILD) sync-pom

pom: clean
	@echo "[pom] -> $(VERSION)"
	@mvn -f $(POMFILE) versions:set versions:commit -DnewVersion="$(VERSION)"
	@mvn -f $(POMFILE) versions:set-scm-tag -DnewTag="$(VERSION)"
	@rm -f $(POMFILE).asc
	@$(MAKE) -s sync-pom

jar: pom
	@echo "[jar]"
	@rm -rf target/classes
	$(BUILD) jar

sig:
	@echo "[sig]"
	@rm -f $(POMFILE).asc
	@gpg2 --armor --detach-sig $(POMFILE)

deploy: clean pom jar
	@echo "[deploy]"
	@mvn -Daether.checksums.omitChecksumsForExtensions= \
	  gpg:sign-and-deploy-file \
	  -Dfile=$(JARFILE) \
	  -DpomFile=$(POMFILE) \
	  -DrepositoryId=clojars \
	  -Durl=https://repo.clojars.org/

tag:
	git tag -s "$(VERSION)" -m "Release $(VERSION)"

clean:
	rm -f target/*.jar $(POMFILE).asc
	find . -name .DS_Store -print0 | xargs -0 rm -f
