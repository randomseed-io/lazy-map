SHELL   := /bin/sh
BUILD   := bin/build
DEPLOY  := bin/deploy
VERSION := $(shell awk 'NF{print $$1; exit}' VERSION)

APPNAME ?= lazy-map

POMFILE := pom.xml
JARFILE := target/$(APPNAME)-$(VERSION).jar

MVN_SYS_PROPS = \
  -Daether.checksums.forSignature=true \
  -Daether.checksums.algorithms=SHA-1,MD5

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
	@test -f "$(JARFILE)" || (echo "Missing $(JARFILE)"; exit 1)
	@test -f "pom.xml" || (echo "Missing pom.xml"; exit 1)
	@echo "[deploy] jar=$(JARFILE)"
	@$(DEPLOY) deploy :artifact "\"$(JARFILE)\""
	@test -f "$(APPNAME)-$(VERSION).pom.asc" && mv -f "$(APPNAME)-$(VERSION).pom.asc" "$(POMFILE).asc" || true

olddeploy: clean pom jar
	@echo "[deploy]"
	@echo "[deploy] MVN_SYS_PROPS=$(MVN_SYS_PROPS)"
	@mvn $(MVN_SYS_PROPS) gpg:sign-and-deploy-file \
	  -DgroupId=io.randomseed \
	  -DartifactId=$(APPNAME) \
	  -Dversion=$(VERSION) \
	  -Dpackaging=jar \
	  -Dfile=$(JARFILE) \
	  -DpomFile=$(POMFILE) \
	  -DrepositoryId=clojars \
	  -Durl=https://repo.clojars.org/

tag:
	git tag -s "$(VERSION)" -m "Release $(VERSION)"

clean:
	@rm -f target/*.jar $(POMFILE).asc
	@find . -name .DS_Store -print0 | xargs -0 rm -f
