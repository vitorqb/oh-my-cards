FROM openjdk:11

ARG USER_ID
ARG GROUP_ID
ARG OHMYCARDS_VERSION

EXPOSE 8000

# Adds an user for running the server 
RUN groupadd -g ${GROUP_ID} ohmycards && useradd -m -u ${USER_ID} -g ${GROUP_ID} ohmycards
USER ohmycards:ohmycards
WORKDIR /home/ohmycards

# Copies the entrypoint
COPY --chown=ohmycards:ohmycards entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

# Copies the generated .tgz artifact file to the image
COPY --chown=ohmycards:ohmycards ./target/universal/oh-my-cards-${OHMYCARDS_VERSION}.tgz /home/ohmycards/oh-my-cards-${OHMYCARDS_VERSION}.tgz

# Untars it
RUN tar -vzxf oh-my-cards-${OHMYCARDS_VERSION}.tgz && rm -rf oh-my-cards-${OHMYCARDS_VERSION}.tgz
RUN ln -s /home/ohmycards/oh-my-cards-${OHMYCARDS_VERSION}/bin/oh-my-cards /home/ohmycards/run
RUN chmod +x /home/ohmycards/run

ENTRYPOINT ["/home/ohmycards/entrypoint.sh"]
CMD []
